import React from 'react';
import {connect} from 'react-redux';
import {SyncOutlined} from '@ant-design/icons';
import {Avatar, Popover, Tooltip} from 'antd';
import {RouteComponentProps, withRouter} from 'react-router';
import DropdownMenu from '../../components/dropdown-menu/dropdown-menu.component';
import Notifications from '../notification/Notifications';
import {IState} from '../../store/index';
import AddProject from '../../components/modals/add-project.component';
import AddProjectItem from '../../components/modals/add-project-item.component';
import {Project, ProjectsWithOwner} from '../project/interface';
import {updateExpandedMyself, updateMyself} from './actions';
import {updateGroups} from '../group/actions';
import {updateNotifications} from '../notification/actions';
import {updateSystem} from '../system/actions';

import './myself.styles.less';
import {updateTaskContents} from "../tasks/actions";
import {Task} from "../tasks/interface";
import {updateNoteContents} from "../notes/actions";
import {Note} from "../notes/interface";
import {updateTransactionContents} from "../transactions/actions";
import {Transaction} from "../transactions/interface";
import {History} from "history";

type MyselfProps = {
  username: string;
  avatar: string;
  ownedProjects: Project[];
  sharedProjects: ProjectsWithOwner[];
  task: Task | undefined;
  note: Note | undefined;
  transaction: Transaction | undefined;
  updateMyself: () => void;
  updateExpandedMyself: (updateSettings: boolean) => void;
  updateGroups: () => void;
  updateNotifications: () => void;
  updateSystem: (force: boolean, history: History<History.PoorMansUnknown>) => void;
  updateTaskContents: (taskId: number) => void;
  updateNoteContents: (noteId: number) => void;
  updateTransactionContents: (transactionId: number) => void;
};

type ModalState = {
  showContactUs: boolean;
};

type PathProps = RouteComponentProps;

class Myself extends React.Component<MyselfProps & PathProps, ModalState> {
  state: ModalState = {
    showContactUs: false,
  };

  interval: any = 0;

  componentDidMount() {
    this.props.updateMyself();
    this.interval = setInterval(() => {
      this.props.updateSystem(false, this.props.history);
    }, 50000);
  }

  componentWillUnmount() {
    clearInterval(this.interval);
  }

  handleRefreshOnClick = () => {
    this.props.updateExpandedMyself(true);
    this.props.updateSystem(true, this.props.history);
    const hash = window.location.hash.toLowerCase();
    if (hash.startsWith('#/note/') && this.props.note) {
      this.props.updateNoteContents(this.props.note.id);
    }

    if (hash.startsWith('#/task/') && this.props.task) {
      this.props.updateTaskContents(this.props.task.id);
    }

    if (hash.startsWith('#/transaction/') && this.props.transaction) {
      this.props.updateTransactionContents(this.props.transaction.id);
    }
  };

  render() {
    let plusIcon;
    if (
      this.props.ownedProjects.length === 0 &&
      this.props.sharedProjects.length === 0
    ) {
      plusIcon = <AddProject history={this.props.history} mode={'complex'} />;
    } else {
      plusIcon = (
        <AddProjectItem history={this.props.history} mode={'complex'} />
      );
    }
    return (
      <div className='myselfContainer'>
        <span id='createNewBuJo'>{plusIcon}</span>
        <Tooltip placement='bottom' title='Refresh'>
          <SyncOutlined
            onClick={this.handleRefreshOnClick}
          />
        </Tooltip>
        <Notifications />
        <Popover
          content={
            <DropdownMenu
              username={this.props.username}
              history={this.props.history}
              showContactUs={this.state.showContactUs}
              onCancelShowContactUs={(
                e: React.MouseEvent<HTMLElement, MouseEvent>
              ) => {
                e.stopPropagation();
                this.setState({ showContactUs: false });
              }}
              handleContact={() => {
                this.setState({ showContactUs: true });
              }}
            />
          }
          trigger='click'
          placement='bottomRight'
        >
          <Avatar
            src={this.props.avatar}
            style={{ cursor: 'pointer', flexShrink: 1 }}
            size={28}
          >
            {this.props.username || 'User'}
          </Avatar>
        </Popover>
      </div>
    );
  }
}

const mapStateToProps = (state: IState) => ({
  username: state.myself.username,
  avatar: state.myself.avatar,
  ownedProjects: state.project.owned,
  sharedProjects: state.project.shared,
  task: state.task.task,
  note: state.note.note,
  transaction: state.transaction.transaction,
});

export default connect(mapStateToProps, {
  updateMyself,
  updateExpandedMyself,
  updateGroups,
  updateNotifications,
  updateSystem,
  updateTaskContents,
  updateNoteContents,
  updateTransactionContents
})(withRouter(Myself));
