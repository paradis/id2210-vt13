/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.search;

import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;

/**
 *
 * @author alban
 */
public class MaxIdTimeout extends Timeout {
    MaxIdTimeout(ScheduleTimeout st) {
        super(st);
    }
}
