package tman.system.peer.tman;

import java.util.UUID;

import cyclon.system.peer.cyclon.DescriptorBuffer;
import java.util.HashSet;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;

public class ExchangeMsg {

    public static class Request extends Message {

        private final HashSet<Address> buffer;

        //-------------------------------------------------------------------
        Request(Address source, Address destination, HashSet<Address> buf) {
            super(source, destination);
            this.buffer = buf;
        }

        //-------------------------------------------------------------------
        public HashSet<Address> getBuffer() {
            return buffer;
        }

    }

    public static class Response extends Message {

        private final HashSet<Address> buffer;

        //-------------------------------------------------------------------
        public Response(Address source, Address destination, HashSet<Address> buf) {
            super(source, destination);
            this.buffer = buf;
        }

        //-------------------------------------------------------------------
        public HashSet<Address> getBuffer() {
            return buffer;
        }
    }

    public static class RequestTimeout extends Timeout {

        private final Address peer;

//-------------------------------------------------------------------
        public RequestTimeout(ScheduleTimeout request, Address peer) {
            super(request);
            this.peer = peer;
        }

//-------------------------------------------------------------------
        public Address getPeer() {
            return peer;
        }
    }
}