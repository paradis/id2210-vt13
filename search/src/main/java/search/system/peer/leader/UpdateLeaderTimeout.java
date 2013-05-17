/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.leader;

import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;

public class UpdateLeaderTimeout extends Timeout {
    UpdateLeaderTimeout(SchedulePeriodicTimeout st) {
        super(st);
    }
}
