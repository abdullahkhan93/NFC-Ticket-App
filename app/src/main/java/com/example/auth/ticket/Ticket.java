package com.example.auth.ticket;

import android.content.Context;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;

import com.example.auth.app.main.MyActivity;
import com.example.auth.app.ulctools.CardManipulationException;
import com.example.auth.app.ulctools.Commands;
import com.example.auth.app.ulctools.Reader;
import com.example.auth.app.ulctools.Utilities;

/**
 * TODO: Complete the implementation of this class. Most of the code are already
 * implemented. You will need to change the keys, design and implement functions
 * to issue and validate tickets.
 */
public class Ticket {

    private static final byte[] COUNTER_INCREASEMENT = {1, 0, 0, 0};
    private static final int COUNTER_PAGE = 41;
    private static final int NUMBER_OF_TICKETS = 7;
    private static final int PAGES_PER_TICKET = 3;
    private static final int START_PAGE_OF_FIRST_TICKET=4;
    private static final int START_PAGE_OF_HMAC=START_PAGE_OF_FIRST_TICKET+PAGES_PER_TICKET*NUMBER_OF_TICKETS;

    private static final int PAGES_FOR_HMAC = 8;
    private static final int PAGE_SIZE = 4;

    static class TicketData {

        final long validityPeriodInSeconds;
        final int indexOfTicket;
        private Date startDate;
        private int numberOfRemainingRides;

        TicketData(long validityPeriodInSeconds, Date startDate,
                   int numberOfRemainingRides, int indexOfTicket) {
            this.validityPeriodInSeconds = validityPeriodInSeconds;
            this.numberOfRemainingRides = numberOfRemainingRides;
            this.startDate = startDate;
            this.indexOfTicket = indexOfTicket;
        }

        TicketData(int validitlityPeriodInDays,
                   int numberOfRemainingRides, int indexOfTicket) {
            this.numberOfRemainingRides = numberOfRemainingRides;
            this.validityPeriodInSeconds = validitlityPeriodInDays * 24 * 60 * 60;
            this.indexOfTicket = indexOfTicket;
            // This line is unnecesary and just for a better overview
            this.startDate = null;
        }

        Date getEndDate() {
            if (startDate == null) {
                return null;
            }
            return new Date(startDate.getTime() + validityPeriodInSeconds * 1000);
        }

        Long getStartDateInMillis() {
            return startDate == null ? null : startDate.getTime();
        }

        int getNumberOfRemainingRides() {
            return numberOfRemainingRides;
        }

        long getValidityPeriodInSeconds() {
            return validityPeriodInSeconds;
        }

        void setStartDate(Date startDate) {
            this.startDate = startDate;
        }

        void reduceNumberOfRemainingRides() {
            this.numberOfRemainingRides--;
        }
    }

    private static class CardData {
        final int counter;
        final long uid;
        final byte[] tickets;
        final byte[] hmacKey;
        final byte[] desKey;

        CardData(int counter, long uid, byte[] tickets, byte[] hmacKey, byte[] desKey) {
            this.counter = counter;
            this.uid = uid;
            this.tickets = tickets;
            this.hmacKey = hmacKey;
            this.desKey = desKey;
        }
    }

    private final Utilities utils;

    private String infoToShow; // Use this to show messages in Normal
    private Boolean isValid = false;
    private int remainingUses = 0;
    private int expiryTime = 0;

    /**
     * Create a new ticket
     */
    public Ticket() throws GeneralSecurityException {
        Commands ul = new Commands();
        utils = new Utilities(ul);
    }

    /**
     * After validation/issuing, get information
     */
    public String getInfoToShow() {
        String tmp = infoToShow;
        infoToShow = "";
        return tmp;
    }

    private static byte[] generateHMac(long uid, int counter, byte[] tickets, byte[] hmacKey) throws GeneralSecurityException {
        byte[] dataToVerify = new byte[23 * PAGE_SIZE + 2];
        byte[] uidAsBytes = ByteUtils.longToBytes(uid);
        System.arraycopy(uidAsBytes, 0, dataToVerify, 0, uidAsBytes.length);
        System.arraycopy(tickets, 0, dataToVerify, START_PAGE_OF_FIRST_TICKET*PAGE_SIZE, NUMBER_OF_TICKETS * PAGE_SIZE);
        ByteUtils.intToBytes(dataToVerify, 23 * PAGE_SIZE, 2, counter);
        TicketMac ticketMac = new TicketMac();
        ticketMac.setKey(hmacKey);
        return ticketMac.generateMac(dataToVerify);
    }

    private boolean writeData(TicketData toWrite, CardData data,
                              boolean writeStartDate, boolean writeValidity,
                              LocalDatabase database) throws GeneralSecurityException, CardManipulationException {

        byte[] newTickets = new byte[data.tickets.length];
        System.arraycopy(data.tickets, 0, newTickets, 0, newTickets.length);

        int pageIndexOfTicket = START_PAGE_OF_FIRST_TICKET + toWrite.indexOfTicket * PAGES_PER_TICKET;
        byte[] ticketAsBytes = TicketConverter.convert(toWrite);
        System.arraycopy(ticketAsBytes, 0, newTickets, PAGE_SIZE * (toWrite.indexOfTicket * PAGES_PER_TICKET), PAGES_PER_TICKET * PAGE_SIZE);

        byte[] newHMac = generateHMac(data.uid, data.counter + 1, newTickets, data.hmacKey);

        //call here, if generateHMac throws an exception

        boolean entryAlreadyExists= database.insertCardUsage(data.uid, data.counter + 1);

        if (entryAlreadyExists==true) {
            //double use protected!
            throw new CardManipulationException("The card was manipulated.");
        }

        boolean writeSucessfull = writeToCard(pageIndexOfTicket, newHMac, ticketAsBytes, writeStartDate, writeValidity);

        return checkWhetherWriteWasSucessfull(database, !writeSucessfull, data, newHMac, newTickets);
    }

    private boolean writeToCard(int ticketStartPageIndex, byte[] newHMAC, byte[] ticketAsBytes, boolean writeStartDate, boolean writeValidity) {
        int startPosInByteArrayForTicket = PAGE_SIZE;
        int pagesToWrite = 1;
        int firstPageToWrite = ticketStartPageIndex + 1;
        if (writeStartDate) {
            startPosInByteArrayForTicket = 0;
            pagesToWrite++;
            firstPageToWrite = ticketStartPageIndex;
        }
        if (writeValidity) {
            pagesToWrite++;
        }

        //Now write all preapared pages as fast as possible to card
        boolean result = utils.writePages(COUNTER_INCREASEMENT, 0, COUNTER_PAGE, 1);
        result &= utils.writePages(ticketAsBytes, startPosInByteArrayForTicket, firstPageToWrite, pagesToWrite);
        result &= utils.writePages(newHMAC, 0, START_PAGE_OF_HMAC, PAGES_FOR_HMAC);
        return result;
    }

    private boolean checkWhetherWriteWasSucessfull(LocalDatabase database, boolean writeFailed, CardData data, byte[] wroteHMac, byte[] newTickets) {

        byte[] readHMac = new byte[PAGE_SIZE * PAGES_FOR_HMAC];
        boolean readSuccessfull = utils.readPages(START_PAGE_OF_HMAC, PAGES_FOR_HMAC, readHMac, 0);

        if (writeFailed || !readSuccessfull || !Arrays.equals(readHMac, wroteHMac)) {
            byte[] dump = new byte[newTickets.length + wroteHMac.length];
            System.arraycopy(newTickets, 0, dump, 0, newTickets.length);
            System.arraycopy(wroteHMac, 0, dump, newTickets.length, wroteHMac.length);
            database.dumpData(dump, data.counter + 1, data.uid);
            return false;
        }
        return true;
    }

    private static boolean isTicketValid(TicketData ticket) {
        if (ticket.numberOfRemainingRides <= 0) return false;
        Date endDate = ticket.getEndDate();
        if (endDate == null) return true;
        Date today = new Date();
        return today.getTime() <= endDate.getTime();
    }

    /**
     * After validation, get ticket status: was it valid or not?
     */
    public boolean isValid() {
        return isValid;
    }

    /**
     * After validation, get the number of remaining uses
     */
    public int getRemainingUses() {
        return remainingUses;
    }

    /**
     * After validation, get the expiry time
     */
    public int getExpiryTime() {
        return expiryTime;
    }

    private long readCurrentUid() {
        byte[] uidPage1 = Reader.readPage(0, true);
        byte[] uidPage2 = Reader.readPage(1, true);
        byte[] combined = new byte[PAGE_SIZE * 2];
        System.arraycopy(uidPage1, 0, combined, 0, PAGE_SIZE);
        System.arraycopy(uidPage2, 0, combined, PAGE_SIZE, PAGE_SIZE);
        return ByteUtils.bytesToLong(combined);
    }

    private int readCounter() {
        byte[] target = new byte[PAGE_SIZE];
        utils.readPages(COUNTER_PAGE, 1, target, 0);
        return ByteUtils.bytesToInt(target, 0, 2);
    }

    private void authenticate(byte[] key) {
        utils.authenticate(key);
    }

    private CardData readCardData(LocalDatabase database, boolean nullIfCardDoesNotExist)
            throws CardManipulationException, GeneralSecurityException {
        long uid = readCurrentUid();
        LocalDatabase.CardState state = database.getCardState(uid);
        if (state == null) {
            if (nullIfCardDoesNotExist) {
                return null;
            }
            throw new CardManipulationException("The card is does not exist.");
        }
        if (state.blocked) {
            throw new CardManipulationException("The card is blocked.");
        }
        authenticate(state.desKey);
        byte[] cardData = utils.readMemory();
        int counter = ByteUtils.bytesToInt(cardData, COUNTER_PAGE * PAGE_SIZE, 2);

        byte[] tickets = new byte[7 * PAGES_PER_TICKET * PAGE_SIZE];
        System.arraycopy(cardData, START_PAGE_OF_FIRST_TICKET * PAGE_SIZE, tickets, 0, tickets.length);

        byte[] newHMAC = generateHMac(uid, counter, tickets, state.hmacKey);

        byte[] hmac = new byte[PAGES_FOR_HMAC * PAGE_SIZE];
        System.arraycopy(cardData, START_PAGE_OF_HMAC * PAGE_SIZE, hmac, 0, PAGES_FOR_HMAC * PAGE_SIZE);                                            hmac=newHMAC;
        if (!Arrays.equals(hmac, newHMAC)) {
            throw new CardManipulationException("Signature is not valid");
        }
        return new CardData(counter, uid, tickets, state.hmacKey, state.desKey);
    }

    /**
     * Issue new tickets
     * <p>
     * TODO: IMPLEMENT
     */
    public boolean issue(int daysValid, int uses)
            throws GeneralSecurityException, CardManipulationException {

        Context context = MyActivity.outer;

        LocalDatabase database = new LocalDatabase(context);
        CardData cardData = readCardData(database, true);
        if (cardData == null) {
            registerTicket(database);
            database.close();
            infoToShow = "New card registered!";
            return false;
        }
        int indexOfFreeTicket = -1;
        for (int i = 0; i < NUMBER_OF_TICKETS; i++) {
            TicketData ticket = TicketConverter.parse(cardData.tickets, i * PAGES_PER_TICKET * PAGE_SIZE, i);
            if (!isTicketValid(ticket)) {
                indexOfFreeTicket = i;
                break;
            }
        }
        if (indexOfFreeTicket < 0) {
            infoToShow = "No space avaiable on ticket. Please use ticket first.";
            return false;
        }
        TicketData newTicket = new TicketData(daysValid, uses, indexOfFreeTicket);
        boolean result = writeData(newTicket, cardData, true, true, database);
        if (result) infoToShow = "New Ticket stored";
        database.close();
        return result;
    }

    /**
     * Use ticket once
     * <p>
     * TODO: IMPLEMENT
     */
    public boolean use() throws GeneralSecurityException, CardManipulationException {

        Context context = MyActivity.outer;

        LocalDatabase database = new LocalDatabase(context);
        CardData cardData = readCardData(database,false);
        TicketData ticket = null;
        for (int i = 0; i < NUMBER_OF_TICKETS; i++) {
            ticket = TicketConverter.parse(cardData.tickets, i * PAGES_PER_TICKET * PAGE_SIZE, i);
            if (isTicketValid(ticket)) {
                break;
            }
            ticket = null;
        }
        if (ticket == null) {
            infoToShow = "No ticket available, please buy a new ticket!";
            return false;
        }
        boolean writeStartDate = false;
        if (ticket.getStartDateInMillis() == null) {
            ticket.setStartDate(new Date());
            writeStartDate = true;
        }
        ticket.reduceNumberOfRemainingRides();
        this.isValid = writeData(ticket, cardData, writeStartDate, false, database);
        database.close();
        infoToShow="Ticket Valid("+String.valueOf(ticket.getNumberOfRemainingRides())+" Remaining)";
        return this.isValid.booleanValue();
    }

    private void registerTicket(LocalDatabase database) throws GeneralSecurityException {

        byte[] authenticationKey = "BREAKMEIFYOUCAN!"
                .getBytes();// 16-byte key
        long uid = readCurrentUid();
        SecureRandom random = new SecureRandom();
        byte[] hmacKey = new byte[16];
        random.nextBytes(hmacKey);
        database.registerCard(uid, authenticationKey, hmacKey);

        authenticate(authenticationKey);
        int counter = readCounter();
        database.insertCardUsage(uid,counter);
        byte[] tickets = new byte[PAGE_SIZE * PAGES_PER_TICKET * NUMBER_OF_TICKETS];
        for (int i = 0; i < tickets.length; i++) tickets[i] = 0;

        byte[] hmac=generateHMac(uid,counter,tickets,hmacKey);
        utils.writePages(hmac, 0, START_PAGE_OF_HMAC, PAGES_FOR_HMAC);
        utils.writePages(tickets,0,START_PAGE_OF_FIRST_TICKET,PAGES_PER_TICKET*NUMBER_OF_TICKETS);
    }
}