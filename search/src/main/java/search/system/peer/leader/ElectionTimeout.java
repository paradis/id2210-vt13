/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.leader;

import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;

/**
 *
 * @author alban
 */
public class ElectionTimeout extends Timeout {
    ElectionTimeout(ScheduleTimeout st) {
        super(st);
    }
}
