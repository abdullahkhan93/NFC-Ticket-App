package com.example.auth.ticket;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import android.database.Cursor;

/**
 * Created by phillip on 10.12.2017.
 */

public class LocalDatabase {

    private static final String CARD_USAGE_TABLE_NAME = "CARD_USAGES";
    private static final String CARD_TABLE_NAME = "CARDS";
    private static final String CARD_CONTENT_TABLE_NAME = "CARDS_CONTENT";
    private static final String UID_COLUMN_NAME ="UID";
    private static final String CARD_CONTENT_COLUMN_NAME ="CONTENT";
    private static final String COUNTER_COLUMN_NAME ="COUNTER";
    private static final String HMAC_KEY_COLUMN_NAME ="HMAC_KEY";
    private static final String DES_KEY_COLUMN_NAME ="DES_KEY";
    private static final String BLOCKED_KEY_COLUMN_NAME ="BLOCKED";

    private static final String INSERT_CARD_DUMP_QUERY;
    private static final String INSERT_CARD_USAGE_QUERY;

    static{
        StringBuilder sb=new StringBuilder("INSERT into ");
        sb.append(CARD_TABLE_NAME);
        sb.append(" (");
        sb.append(UID_COLUMN_NAME);
        sb.append(", ");
        sb.append(COUNTER_COLUMN_NAME);
        sb.append(", ");
        sb.append(CARD_CONTENT_COLUMN_NAME);
        sb.append(") VALUES (?,?,?);");
        INSERT_CARD_DUMP_QUERY = sb.toString();
    }

    static{
        StringBuilder sb=new StringBuilder("INSERT into ");
        sb.append(CARD_USAGE_TABLE_NAME);
        sb.append(" (");
        sb.append(UID_COLUMN_NAME);
        sb.append(", ");
        sb.append(COUNTER_COLUMN_NAME);
        sb.append(") VALUES (?,?);");
        INSERT_CARD_USAGE_QUERY = sb.toString();
    }

    private static class SQLLiteHelper extends SQLiteOpenHelper {

        private static final String CREATE_CARD_USAGES_TABLE_STATEMENT = "CREATE TABLE " +
                CARD_USAGE_TABLE_NAME + "("+UID_COLUMN_NAME+" int(64) NOT NULL, " + COUNTER_COLUMN_NAME+
                " int(32) NOT NULL)";

        private static final String CREATE_CARD_TABLE_STATEMENT = "CREATE TABLE " + CARD_TABLE_NAME
                + " ( "+UID_COLUMN_NAME+" INT(64) NOT NULL , "+BLOCKED_KEY_COLUMN_NAME+" INT(1) default 0, "+HMAC_KEY_COLUMN_NAME+" BLOB NOT NULL , " +
                DES_KEY_COLUMN_NAME+"  BLOB NOT NULL , PRIMARY KEY (UID))";

        private static final String CREATE_CARD_CONTENT_TABLE_STATEMENT = "CREATE TABLE " +
                CARD_CONTENT_TABLE_NAME + " ( "+UID_COLUMN_NAME+" INT(64) NOT NULL , "+CARD_CONTENT_COLUMN_NAME
                +" BLOB NOT NULL , "+COUNTER_COLUMN_NAME+" INT(32) NOT NULL )";

        private static final String DATABASE_NAME = "Database";

        private SQLLiteHelper(Context context) {
            super(context, DATABASE_NAME, null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            sqLiteDatabase.execSQL(CREATE_CARD_USAGES_TABLE_STATEMENT);
            sqLiteDatabase.execSQL(CREATE_CARD_TABLE_STATEMENT);
            sqLiteDatabase.execSQL(CREATE_CARD_CONTENT_TABLE_STATEMENT);
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
            sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + CARD_TABLE_NAME);
            sqLiteDatabase.execSQL("DROP TABLE IF EXISTS "+CARD_USAGE_TABLE_NAME);
            sqLiteDatabase.execSQL("DROP TABLE IF EXISTS "+CARD_CONTENT_TABLE_NAME);
            onCreate(sqLiteDatabase);
        }
    }

    static class CardState {
        final boolean blocked;
        final byte[] hmacKey;
        final byte[] desKey;

        private CardState(boolean blocked, byte[]hmacKey,byte[]desKey){
            this.blocked=blocked;
            this.hmacKey=hmacKey;
            this.desKey=desKey;
        }
    }

    private final SQLiteDatabase litedb;

    LocalDatabase(Context context) {
        SQLLiteHelper bd = new SQLLiteHelper(context);
        litedb = bd.getWritableDatabase();
    }

    void registerCard(long uid, byte[] authenticationKey, byte[] hmacKey){
        ContentValues values = new ContentValues();
        values.put(UID_COLUMN_NAME,uid);
        values.put(BLOCKED_KEY_COLUMN_NAME,false);
        values.put(HMAC_KEY_COLUMN_NAME,hmacKey);
        values.put(DES_KEY_COLUMN_NAME,authenticationKey);
        litedb.insert(CARD_TABLE_NAME,"null",values);

        SQLiteStatement stmt= litedb.compileStatement("delete from " + CARD_USAGE_TABLE_NAME+" where "+UID_COLUMN_NAME+"= ?");
        stmt.clearBindings();
        stmt.bindLong(1,uid);
        stmt.executeUpdateDelete();
    }

    private boolean usageExists(long uid,int counter){
        String whereClause=UID_COLUMN_NAME+"= ? and "+COUNTER_COLUMN_NAME+" = ?";

        String[] selectionArgs={Long.toString(uid),Integer.toString(counter)};
        Cursor cursor = litedb.query(CARD_USAGE_TABLE_NAME,null,whereClause,selectionArgs,null,null,null,null);
        int count =cursor.getCount();
        cursor.close();
        return count >0;
    }
    /**
     *
     * @param uid
     * @param counter
     * @return whether combination of counter and uid already exists in database
     */
    boolean insertCardUsage(long uid,int counter){

        if (usageExists(uid,counter))return true;

        ContentValues values=new ContentValues();
        values.put(UID_COLUMN_NAME,uid);
        values.put(COUNTER_COLUMN_NAME,counter);
        litedb.insert(CARD_USAGE_TABLE_NAME,"null",values);
        return false;
    }

    CardState getCardState(long uid){
        String[] columns = {"BLOCKED", "HMAC_KEY", "DES_KEY"};
        String[] where = {String.valueOf(uid)};
        Cursor c = litedb.query(
                "CARDS",
                columns,
                "UID=?",
                where,
                null,
                null,
                null);

        CardState cs;
        if (c.moveToFirst()) {
            cs = new CardState(c.getInt(0)!=0,c.getBlob(1),c.getBlob(2));
        }else{
            cs=null;
        }
        c.close();
        return cs;
    }

    void dumpData(byte[] content,int counter, long uid){

        ContentValues values=new ContentValues();
        values.put(UID_COLUMN_NAME,uid);
        values.put(COUNTER_COLUMN_NAME,counter);
        values.put(CARD_CONTENT_COLUMN_NAME,content);
        litedb.insert(CARD_CONTENT_TABLE_NAME,"null",values);
    }

    void close(){
        litedb.close();
    }
}
