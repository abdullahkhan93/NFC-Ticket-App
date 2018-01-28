package com.example.auth.ticket;

import com.example.auth.ticket.Ticket.TicketData;
import java.util.Date;


/**
 * Created by phillip on 10.12.2017.
 */

public abstract class TicketConverter {

    private static final int PAGE_SIZE=4;

    private static byte[] createCopy(byte[] src, int startPos, int length) {
        byte[] result = new byte[length];
        System.arraycopy(src, startPos, result, 0, length);
        return result;
    }

    static TicketData parse(byte[] bytes, int indexOfFirstByteOfTicket, int indexOfThisTicket) {
        long startDateInSeconds = ByteUtils.bytesToLongSmall(bytes,indexOfFirstByteOfTicket);
        long validityPeriodInSeconds = ByteUtils.bytesToLongSmall(bytes,indexOfFirstByteOfTicket+ 8);
        int numOfRemainingRides = ByteUtils.bytesToInt(bytes,indexOfFirstByteOfTicket + 6,2);
        Date startDate=startDateInSeconds==0?null:new Date(startDateInSeconds*1000L);
        return new TicketData(validityPeriodInSeconds, startDate, numOfRemainingRides, indexOfThisTicket);
    }

    static byte[] convert(TicketData data) {
        Long startDateNullable = data.getStartDateInMillis();
        long startDateInMillis = startDateNullable == null ? 0L : startDateNullable.longValue();
        long startDateInSecconds=startDateInMillis/1000;

        byte[] ticketAsBytes=new byte[PAGE_SIZE*3];

        ByteUtils.longToBytesSmall(ticketAsBytes,0,startDateInSecconds);

        ticketAsBytes[4] = 0;
        ticketAsBytes[5] = 0;
        ByteUtils.intToBytes(ticketAsBytes,1*PAGE_SIZE+2,2,data.getNumberOfRemainingRides());

        ByteUtils.longToBytesSmall(ticketAsBytes,2*PAGE_SIZE,data.getValidityPeriodInSeconds());
        return ticketAsBytes;
    }
}
