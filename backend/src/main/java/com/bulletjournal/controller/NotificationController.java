package com.bulletjournal.controller;

import com.bulletjournal.clients.UserClient;
import com.bulletjournal.controller.models.AnswerNotificationParams;
import com.bulletjournal.controller.models.Notification;
import com.bulletjournal.controller.utils.EtagGenerator;
import com.bulletjournal.exceptions.ResourceNotFoundException;
import com.bulletjournal.notifications.*;
import com.bulletjournal.redis.RedisEtagDaoJpa;
import com.bulletjournal.redis.models.EtagType;
import com.bulletjournal.repository.*;
import com.bulletjournal.repository.models.Group;
import com.bulletjournal.repository.models.User;
import com.bulletjournal.repository.models.UserGroup;
import com.bulletjournal.repository.models.UserGroupKey;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

@RestController
public class NotificationController {
    protected static final String NOTIFICATIONS_ROUTE = "/api/notifications";
    protected static final String ANSWER_NOTIFICATION_ROUTE = "/api/notifications/{notificationId}/answer";
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationController.class);
    @Autowired
    private NotificationDaoJpa notificationDaoJpa;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private UserDaoJpa userDaoJpa;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private RedisEtagDaoJpa redisEtagDaoJpa;

    @GetMapping(NOTIFICATIONS_ROUTE)
    public ResponseEntity<List<Notification>> getNotifications() {
        String username = MDC.get(UserClient.USER_NAME_KEY);
        List<Notification> notificationList = this.notificationDaoJpa.getNotifications(username);

        String notificationsEtag = EtagGenerator.generateEtag(EtagGenerator.HashAlgorithm.MD5,
                EtagGenerator.HashType.TO_HASHCODE,
                notificationList);

        HttpHeaders responseHeader = new HttpHeaders();
        responseHeader.setETag(notificationsEtag);

        // Store Etag to cache
        redisEtagDaoJpa.singleCache(username, EtagType.NOTIFICATION, notificationsEtag);

        return ResponseEntity.ok().headers(responseHeader).body(notificationList);
    }

    @PostMapping(ANSWER_NOTIFICATION_ROUTE)
    public ResponseEntity<?> answerNotification(
            @NotNull @PathVariable Long notificationId,
            @Valid @RequestBody AnswerNotificationParams answerNotificationParams) {
        String username = MDC.get(UserClient.USER_NAME_KEY);
        com.bulletjournal.repository.models.Notification notification =
                this.notificationRepository.findById(notificationId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException("Notification " + notificationId + " not found"));
        deleteNotification(notification);
        Informed informed = processNotification(notification, answerNotificationParams, username);
        if (informed != null) {
            this.notificationService.inform(informed);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(NOTIFICATIONS_ROUTE)
    public ResponseEntity<List<Notification>> cleanNotifications() {
        String username = MDC.get(UserClient.USER_NAME_KEY);
        this.notificationRepository.deleteByTargetUser(username);
        return getNotifications();
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    void deleteNotification(com.bulletjournal.repository.models.Notification notification) {
        this.notificationRepository.delete(notification);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public Informed processNotification(
            com.bulletjournal.repository.models.Notification notification,
            AnswerNotificationParams answerNotificationParams,
            String owner) {
        switch (notification.getType()) {
            case "JoinGroupEvent":
                String answerAction = answerNotificationParams.getAction();
                Preconditions.checkNotNull(answerAction, "For JoinGroupEvent, action is required");
                Action action = Action.getAction(answerAction);
                User user = this.userDaoJpa.getByName(notification.getTargetUser());
                UserGroupKey userGroupKey = new UserGroupKey(user.getId(), notification.getContentId());
                UserGroup userGroup = this.userGroupRepository.findById(userGroupKey)
                        .orElseThrow(() ->
                                new ResourceNotFoundException("UserGroupKey not found"));
                if (Action.ACCEPT.equals(action)) {
                    // set UserGroup's "accepted" to be true
                    userGroup.setAccepted(true);
                    this.userGroupRepository.save(userGroup);
                } else {
                    // remove UserGroup
                    this.userGroupRepository.delete(userGroup);
                }

                Group group = this.groupRepository.findById(notification.getContentId()).orElseThrow(() ->
                        new ResourceNotFoundException("Group " + notification.getContentId() + " not found"));
                Event event = new Event(
                        notification.getOriginator(),
                        notification.getContentId(),
                        group.getName());
                return new JoinGroupResponseEvent(event, owner, action);
        }

        return null;
    }
}
