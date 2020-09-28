package com.bulletjournal.repository;

import com.bulletjournal.authz.Role;
import com.bulletjournal.controller.models.Theme;
import com.bulletjournal.controller.models.UpdateMyselfParams;
import com.bulletjournal.controller.models.UserPointActivity;
import com.bulletjournal.exceptions.ResourceAlreadyExistException;
import com.bulletjournal.exceptions.ResourceNotFoundException;
import com.bulletjournal.redis.FirstTimeUserRepository;
import com.bulletjournal.redis.models.FirstTimeUser;
import com.bulletjournal.repository.models.Group;
import com.bulletjournal.repository.models.User;
import com.bulletjournal.repository.models.UserGroup;
import com.bulletjournal.repository.utils.DaoHelper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Repository
public class UserDaoJpa {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private LabelDaoJpa labelDaoJpa;

    @Autowired
    private FirstTimeUserRepository firstTimeUserRepository;

    @Autowired
    private UserPointActivityDaoJpa userPointActivityDaoJpa;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public User create(String name, String timezone) {
        User existingUser = this.userRepository.findByName(name);
        if (existingUser != null) {
            throw new ResourceAlreadyExistException("User " + name + " already exists");
        }

        User user = new User();
        user.setName(name);
        user.setTimezone(timezone);
        user.setDateFormat(0);
        user.setTimeFormat(0);
        user.setCurrency("US");
        user.setTheme(Theme.LIGHT.name());
        user.setPoints(6);
        user = this.userRepository.save(user);

        Group group = new Group();
        group.setName(Group.DEFAULT_NAME);
        group.setOwner(name);
        group.setDefaultGroup(true);
        group = this.groupRepository.save(group);

        user.addGroup(group);
        this.userGroupRepository.save(new UserGroup(user, group, true));
        this.labelDaoJpa.createDefaultLabels(name);
        user = this.userRepository.save(user);
        this.firstTimeUserRepository.save(new FirstTimeUser(user.getName()));
        return user;
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public User getByName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Missing username");
        }

        User existingUser = this.userRepository.findByName(name);
        if (existingUser == null) {
            throw new ResourceNotFoundException("User " + name + " does not exist");
        }
        return existingUser;
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public List<User> getUsersByNames(Set<String> usernames) {
        List<User> ret = userRepository.findAllByNameIn(usernames);
        if (ret.isEmpty()) {
            throw new ResourceNotFoundException("Non of usernames in '" + usernames + "' exist");
        }
        return ret;
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public User updateMyself(String user, UpdateMyselfParams updateMyselfParams) {
        User self = getByName(user);
        DaoHelper.updateIfPresent(updateMyselfParams.hasTimezone(),
            updateMyselfParams.getTimezone(), self::setTimezone);
        DaoHelper.updateIfPresent(updateMyselfParams.hasReminderBeforeTask(),
                updateMyselfParams.getReminderBeforeTask(), self::setReminderBeforeTask);
        DaoHelper.updateIfPresent(updateMyselfParams.hasCurrency(),
            updateMyselfParams.getCurrency(), self::setCurrency);
        DaoHelper.updateIfPresent(updateMyselfParams.hasTheme(),
            updateMyselfParams.getTheme(), self::setTheme);
        DaoHelper.updateIfPresent(updateMyselfParams.hasEmail(),
            updateMyselfParams.getEmail(), self::setEmail);
        return self;
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public boolean isAdmin(String username) {
        return Role.getType(this.getByName(username).getRole()).equals(Role.ADMIN);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void setRole(String username, Role role) {
        User user = this.getByName(username);
        user.setRole(role.getValue());
        this.userRepository.save(user);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public List<User> getUsersByRole(Role role) {
        List<User> users = this.userRepository.getUsersByRole(role.getValue());
        return users;
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void changeUserPoints(String username, Integer points, String description) {
        userPointActivityDaoJpa.create(username, points, description);
        User user = this.getByName(username);
        Integer pts = user.getPoints() + points;
        user.setPoints(pts);
        this.userRepository.save(user);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public List<UserPointActivity> getPointActivitiesByUsername(String username) {
        return userPointActivityDaoJpa.findPointActivityByUsername(username);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void setUserPoints(String username, Integer points) {
        User user = this.getByName(username);
        user.setPoints(points);
        this.userRepository.save(user);
    }
}
