package com.together.linkalk;

/**
 * Created by kimhj on 2017-08-01.
 */

public class Chat {
    String sender;
    String Receiver;
    String msg;
    String time;
    int sync;

    Chat(String S, String R, String M, String T, int s){
        sender = S;
        Receiver = R;
        msg = M;
        time = T;
        sync = s;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return Receiver;
    }

    public String getMsg() {
        return msg;
    }

    public String getTime() {
        return time;
    }

    public int getSync() {
        return sync;
    }
}