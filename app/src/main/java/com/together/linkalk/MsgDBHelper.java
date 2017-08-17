package com.together.linkalk;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.widget.Adapter;
import android.widget.ListView;

/**
 * Created by kimhj on 2017-08-01.
 */

public class MsgDBHelper extends SQLiteOpenHelper{

    public static final String DB_NAME = "Chat.db";
    public static final int DB_VERSION = 1;

    Handler handler;

    public MsgDBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS chat_room (roomNo INTEGER, relation TEXT, ordered INTEGER);");
        db.execSQL("CREATE TABLE IF NOT EXISTS chat_msg (roomNo INTEGER, msgNo INTEGER, sender TEXT, message TEXT, time TEXT, readed INTEGER, sync INTEGER);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public void insertRoom(int no, String rel){
        SQLiteDatabase db = getWritableDatabase();
        String query = "SELECT * FROM chat_room where roomNo='"+no+"'";
        Cursor cursor = db.rawQuery(query, null);
        if(cursor.getCount() == 0){
            db.execSQL("INSERT INTO chat_room VALUES('"+no+"', '"+rel+"','0');");
            db.close();
        }
    }

    public void insertMsg(String dis1, String dis2, String sender, String msg, String time, int readed, int sync){
        SQLiteDatabase db = getWritableDatabase();
        String query = "SELECT roomNo, ordered FROM chat_room WHERE relation='"+dis1+"' OR relation='"+dis2+"'";
        Cursor cursor = db.rawQuery(query, null);
        int rn = 0;
        int or = 0;
        if(cursor.moveToFirst()){
            rn =  cursor.getInt(cursor.getColumnIndex("roomNo"));
            or = cursor.getInt(cursor.getColumnIndex("ordered"));
        }

        String query2 = "SELECT msgNo FROM chat_msg WHERE roomNo='"+rn+"'";
        cursor = db.rawQuery(query2, null);
        int mn = cursor.getCount();

        db.execSQL("INSERT INTO chat_msg VALUES('"+rn+"', '"+mn+"', '"+sender+"', '"+msg+"', '"+time+"', '"+readed+"', '"+sync+"');");

        cursor = db.rawQuery("SELECT * FROM chat_room ORDER BY ordered ASC;", null);
        while(cursor.moveToNext()){
            if((cursor.getInt(cursor.getColumnIndex("roomNo")) != rn) && cursor.getInt(cursor.getColumnIndex("ordered"))<=or){
                int this_rn = cursor.getInt(cursor.getColumnIndex("roomNo"));
                int save_or = cursor.getInt(cursor.getColumnIndex("ordered")) + 1;
                db.execSQL("UPDATE chat_room SET ordered = '"+ save_or +"' WHERE roomNo = '"+ this_rn +"'");
            }
            if(cursor.getInt(cursor.getColumnIndex("roomNo")) == rn){
                db.execSQL("UPDATE chat_room SET ordered = '0' WHERE roomNo='"+rn+"'");
                break;
            }
        }

        db.close();
    }

    // 앱 실행 후 대화방 들어갈 때 메시지 로딩
    public void selectMsg(String p1, String p2, ListView lv, ChatCommunication_Adapter ad){
        String dis1 = p1 + "/" + p2;
        String dis2 = p2 + "/" + p1;

        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT roomNo FROM chat_room WHERE relation='"+dis1+"' OR relation='"+dis2+"'";
        Cursor cursor = db.rawQuery(query, null);
        int rn = 0;
        if(cursor.moveToFirst()){
            rn =  cursor.getInt(0);
        }

        query = "SELECT * FROM chat_msg WHERE roomNo='"+rn+"' ORDER BY msgNo ASC";
        Cursor c = db.rawQuery(query, null);
        if(c.moveToFirst()){
            do{
                int mn = c.getInt(1);
                String sender = c.getString(2);
                String msg = c.getString(3);
                String time = c.getString(4);
                if(sender.equals(p1)){
                    Chat chat = new Chat(sender, p2, msg, time, 1);
                    ad.addItem(chat);
                    db.execSQL("UPDATE chat_msg SET readed='2' WHERE roomNo='"+rn+"' and msgNo='"+mn+"'");
                } else if(sender.equals(p2)){
                    Chat chat = new Chat(p2, p1, msg, time, 1);
                    ad.addItem(chat);
                    db.execSQL("UPDATE chat_msg SET readed='2' WHERE roomNo='"+rn+"' and msgNo='"+mn+"'");
                }
            }while(c.moveToNext());
        }
        cursor.close();
        c.close();
        db.close();
    }

    // 대화방에 들어와 있을 때, 새로 도착한 메시지 로딩
    public void continueSelectMsg(Handler handler, String p1, String p2, final ListView listView, final ChatCommunication_Adapter ad){
        this.handler = handler;

        String dis1 = p1 + "/" + p2;
        String dis2 = p2 + "/" + p1;

        SQLiteDatabase db = getWritableDatabase();
        String query = "SELECT roomNo FROM chat_room WHERE relation='"+dis1+"' OR relation='"+dis2+"'";
        Cursor cursor = db.rawQuery(query, null);
        int rn = 0;
        if(cursor.moveToFirst()){
            rn =  cursor.getInt(0);
        }

        query = "SELECT * FROM chat_msg WHERE roomNo='"+rn+"' and readed='1' ORDER BY msgNo ASC";
        Cursor c = db.rawQuery(query, null);
        if(c.moveToFirst()){
            do{
                int mn = c.getInt(1);
                String sender = c.getString(2);
                String msg = c.getString(3);
                String time = c.getString(4);
                if(sender.equals(p1)){
                    Chat chat = new Chat(sender, p2, msg, time, 1);
                    ad.addItem(chat);
                    db.execSQL("UPDATE chat_msg SET readed='2' WHERE roomNo='"+rn+"' and msgNo='"+mn+"'");
                } else if(sender.equals(p2)){
                    Chat chat = new Chat(p2, p1, msg, time, 1);
                    ad.addItem(chat);
                    db.execSQL("UPDATE chat_msg SET readed='2' WHERE roomNo='"+rn+"' and msgNo='"+mn+"'");
                }
            }while(c.moveToNext());
            this.handler.post(new Runnable() {
                @Override
                public void run() {
                    listView.setAdapter(ad);
                    listView.setSelection(ad.getCount()-1);
                }
            });
        }

        cursor.close();
        c.close();
        db.close();
    }

}