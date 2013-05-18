/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.leader;

import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;

/**
 * Timeout event for leader info requests ; corresponds to LeaderMsg.AskLeaderInfos and LeaderMsg.AnswerLeaderInfos messages
 * @author alban
 */
public class LeaderInfosTimeout extends Timeout {
    LeaderInfosTimeout(ScheduleTimeout st) {
        super(st);
    }
}
