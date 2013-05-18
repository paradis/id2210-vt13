/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.leader;

import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;

/**
 * Timeout for election. Any elector that didn't answer "no" before the timeout is considered to have answered "yes".
 * Corresponds to LeaderMsg.Apply, LeaderMsg.Accept and LeaderMsg.Reject messages
 * @author alban
 */
public class ElectionTimeout extends Timeout {
    ElectionTimeout(ScheduleTimeout st) {
        super(st);
    }
}
