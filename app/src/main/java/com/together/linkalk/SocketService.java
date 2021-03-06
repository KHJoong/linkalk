package com.together.linkalk;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.vision.text.Text;
import com.google.firebase.iid.FirebaseInstanceId;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by kimhj on 2017-08-16.
 */

public class SocketService extends Service{

    String ip = "115.71.232.230"; // IP
    int port = 9999; // PORT번호

    static Socket socket;
    Thread msgReceiver;
    static DataOutputStream dos;
    static DataInputStream in;

    String my_nickname;

    MsgDBHelper msgDBHelper;
    String sender;
    String receiver;
    String receiver2;
    ArrayList<String> receiver_array;
    int type;
    String lan;
    String msg;
    String time;

    String post_msg ="";

    IntentFilter intentFilter;
    BroadcastReceiver broadcastReceiver;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // 메시지 수신 쓰레드 실행
        msgReceiver = new Thread(new MsgReceiver(getApplicationContext()));

        // 토큰 업데이트 쓰레드 실행
        UpdateMyToken umt = new UpdateMyToken();
        umt.execute();

        // 소켓 연결 안되었을 때 저장된 메시지 받아오는 asynctask 실행
        GetTmpMsg getTmpMsg = new GetTmpMsg(getApplicationContext());
        getTmpMsg.execute();
//        GetTmpMessage gtm = new GetTmpMessage(getApplicationContext());
//        gtm.start();

        // 새로운 소켓 선언
        socket = new Socket();

        new Thread(new Runnable() {
            @Override
            public void run() {
                // 서버에 연결할 때 보낼 내 닉네임
                SharedPreferences sharedPreferences = getSharedPreferences("maintain", MODE_PRIVATE);
                my_nickname = sharedPreferences.getString("nickname", "");

                try {
                    SocketAddress sock_addr = new InetSocketAddress(ip, port);
                    socket.connect(sock_addr);

                    dos = new DataOutputStream(socket.getOutputStream());
                    in = new DataInputStream(socket.getInputStream());
                    dos.writeUTF(my_nickname);
                    dos.flush();

                    msgReceiver.start();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.ACTION_SHUTDOWN");
        intentFilter.addAction("android.intent.action.REBOOT");
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals("android.intent.action.ACTION_SHUTDOWN") || intent.getAction().equals("android.intent.action.REBOOT")) {
                    msgReceiver.interrupt();
                    try {
                        dos.close();
                        in.close();
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    unregisterReceiver(broadcastReceiver);
                }
            }
        };
        registerReceiver(broadcastReceiver, intentFilter);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        msgReceiver.interrupt();
        try {
            dos.close();
            in.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        unregisterReceiver(broadcastReceiver);
    }

    // 메시지 받는 쓰레드
    class MsgReceiver extends Thread{
        Context mContext;

        Handler handler = new Handler();

        public MsgReceiver(Context context) {
            this.mContext = context;
        }

        public void run() {
            while(in != null) {//서버에서 받은 데이터를 뽑아냅니다.
                try {
                    final String getString = in.readUTF();    //1
                    System.out.println("settttt" + getString);
                    if(!TextUtils.isEmpty(getString)){
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                SimpleDateFormat sdfNow = new SimpleDateFormat("yyyy/MM/dd/HH/mm/ss");
                                time = sdfNow.format(new Date(System.currentTimeMillis()));
                                sender = "";
                                receiver = "";
                                receiver2 = "";
                                receiver_array = new ArrayList<String>();
                                type = 0;
                                msg = "비어있음";
                                int sync = 1;
                                try {
                                    JSONObject obj = new JSONObject(getString);
                                    sender = obj.getString("sender");
                                    JSONArray array = obj.getJSONArray("receiver");
                                    for(int i=0; i<array.length(); i++){
                                        receiver_array.add(array.getString(i));
                                        if(i==(array.length()-1)){
                                            receiver = receiver+ array.getString(i);
                                        } else {
                                            receiver = receiver+ array.getString(i)+"/";
                                        }
                                        if((array.length()-1-i)==0){
                                            receiver2 = receiver2 + array.getString(array.length()-1-i);
                                        } else {
                                            receiver2 = receiver2 + array.getString(array.length()-1-i) + "/";
                                        }
                                    }
                                    type = obj.getInt("type");
                                    msg = obj.getString("msg");
                                    lan = obj.getString("language");
                                    sync = 1;
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                                // 친구 아닌사람이 보낸 메시지면 사진 가져오는 async
                                GetNotFriendPic gnfp = new GetNotFriendPic(getApplicationContext());
                                gnfp.execute(sender);

                                if(type==1){    // 사진이 아니면 번역 메시지로 변경
                                    returnTransMsg rtm = new returnTransMsg(mContext, sender, receiver, type, msg, lan);
                                    rtm.execute();
                                } else if(type==2){     // 사진이면 그냥 저장할 수 있도록
                                    msgDBHelper = new MsgDBHelper(mContext);

                                    // 메시지가 왔는데 기존에 있는 방인지 확인
                                    String checkExistRoom = "SELECT * FROM chat_room WHERE relation = '"+receiver+"' OR relation='"+receiver2+"';";
                                    SQLiteDatabase db = msgDBHelper.getReadableDatabase();
                                    Cursor c = db.rawQuery(checkExistRoom, null);
                                    int existRoom = c.getCount();
                                    if(existRoom==0){
                                        // 방이 없으면
                                        JSONObject jsonObject = new JSONObject();
                                        try {
                                            jsonObject.put("sender", sender);
                                            jsonObject.put("receiver", new JSONArray(receiver_array));
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }

                                        // 채팅방 DB 생성하기
                                        GetChatRoom gcr = new GetChatRoom(mContext);
                                        gcr.execute(jsonObject.toString());
                                    } else {
                                        // 방이 있으면
                                        msgDBHelper.insertMsg(sender, receiver, receiver2, type, msg, msg, time, 1, 1);

                                        // 새로운 메시지가 추가됐음을 알리기 위한 Broadcast
                                        // 이 Broadcast를 받아서 채팅방의 순서를 재정렬함
                                        // MainChatFragment
                                        Intent intent = new Intent();
                                        intent.setAction("com.together.broadcast.room.integer");
                                        intent.putExtra("reload", 1);
                                        sendBroadcast(intent);

                                        // 새로운 메시지가 추가됐음을 알리기 위한 Broadcast
                                        // 이 Broadcast를 받아서 새로 온 메시지를 리스트에 추가함
                                        // InChattingActivity
                                        Intent intent2 = new Intent();
                                        intent2.setAction("com.together.broadcast.chat.integer");
                                        intent2.putExtra("plus", 1);
                                        intent2.putExtra("type", type);
                                        intent2.putExtra("msg", post_msg);
                                        intent2.putExtra("dis1", receiver);
                                        intent2.putExtra("dis2", receiver2);
                                        intent2.putExtra("Receiver", sender);
                                        intent2.putStringArrayListExtra("ReceiverArray", receiver_array);
                                        sendBroadcast(intent2);

                                        // 현재 보여주고 있는 최상위 Activity가 뭔지 출력해주는 부분/ 채팅방에 들어가있는 상태가 아니면 노티 띄워줌
                                        // com.together.linkalk.InChattingActivity
                                        ActivityManager am = (ActivityManager) mContext.getSystemService(ACTIVITY_SERVICE);
                                        List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);
                                        Log.d("topActivity", "CURRENT Activity ::" + taskInfo.get(0).topActivity.getClassName());
                                        // 방 번호 찾는 쿼리
                                        String query = "SELECT roomNo FROM chat_room WHERE relation='"+receiver+"' OR relation='"+receiver2+"'";
                                        db = msgDBHelper.getReadableDatabase();
                                        c = db.rawQuery(query, null);
                                        int rn = 0;
                                        if(c.moveToFirst()){
                                            rn =  c.getInt(c.getColumnIndex("roomNo"));
                                        }
                                        if(!taskInfo.get(0).topActivity.getClassName().equals("com.together.linkalk.InChattingActivity") && !sender.equals(my_nickname)){
                                            NotificationManager notificationManager= (NotificationManager)mContext.getSystemService(NOTIFICATION_SERVICE);
                                            Intent intent3 = new Intent(mContext, InChattingActivity.class);
                                            intent3.putStringArrayListExtra("Receiver", receiver_array);
                                            Notification.Builder builder = new Notification.Builder(mContext);
                                            PendingIntent pendingIntent = PendingIntent.getActivity(mContext, (int)System.currentTimeMillis()/1000, intent3, PendingIntent.FLAG_UPDATE_CURRENT);
                                            builder.setSmallIcon(R.mipmap.ic_launcher_round)
                                                    .setTicker("Linkalk")
                                                    .setWhen(System.currentTimeMillis())
                                                    .setContentTitle(sender).setContentText("사진이 도착했습니다.")
                                                    .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                                                    .setContentIntent(pendingIntent)
                                                    .setAutoCancel(true)
                                                    .setOngoing(false);
                                            notificationManager.notify(rn, builder.build());
                                        }
                                    }
                                    c.close();
                                    db.close();
                                }

                            }
                        });
                    }
                } catch(IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }   // client receiver end

    // 상대방이 말을 걸었을 경우 채팅방 번호 받아서 추가하는 Asynctask
    class GetChatRoom extends AsyncTask<String, Void, String> {

        Context mContext;

        GetChatRoom(Context context){
            mContext = context;
        }

        @Override
        protected String doInBackground(String... params) {
            try{
                String send = params[0];

                // 서버와 연결하기 위해 세션 아이디 불러와서 커넥트
                SharedPreferences sharedPreferences = getSharedPreferences("maintain", Context.MODE_PRIVATE);
                String sessionID = sharedPreferences.getString("sessionID", "");

                Log.i("sessionID",sessionID);

                URL url = new URL("http://www.o-ddang.com/linkalk/checkChatRoom.php");
                HttpURLConnection httpURLConnection = (HttpURLConnection)url.openConnection();

                httpURLConnection.setReadTimeout(5000);
                httpURLConnection.setConnectTimeout(5000);
                httpURLConnection.setDefaultUseCaches(false);
                httpURLConnection.setDoInput(true);
                httpURLConnection.setDoOutput(true);
                httpURLConnection.setRequestMethod("POST");

                httpURLConnection.setInstanceFollowRedirects( false );
                if(!TextUtils.isEmpty(sessionID)) {
                    httpURLConnection.setRequestProperty( "cookie", sessionID) ;
                }

                httpURLConnection.setRequestProperty("Accept", "application/json");
                httpURLConnection.setRequestProperty("Content-type", "application/json");

                OutputStream os = httpURLConnection.getOutputStream();
                os.write(send.getBytes());
                os.flush();

                // 서버에서 채팅룸 번호랑 대화 상대 전달
                int responseStatusCode = httpURLConnection.getResponseCode();
                Log.d("responseStatusCode", "response code - " + responseStatusCode);

                InputStream inputStream;
                if(responseStatusCode == HttpURLConnection.HTTP_OK) {
                    inputStream = httpURLConnection.getInputStream();
                } else{
                    inputStream = httpURLConnection.getErrorStream();
                }
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

                StringBuilder sb = new StringBuilder();
                String line;

                while((line = bufferedReader.readLine()) != null){
                    sb.append(line);
                }
                bufferedReader.close();
                return sb.toString().trim();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return e.getMessage();
            } catch (ProtocolException e) {
                e.printStackTrace();
                return e.getMessage();
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return e.getMessage();
            } catch (IOException e) {
                e.printStackTrace();
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            Log.i("MyFriendProfile", String.valueOf(s));
            int roomNo = 0;
            String relation = null;
            try {
                JSONObject object = new JSONObject(s);
                roomNo = object.getInt("roomNo");
                relation = object.getString("relation");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            // SQLite에 추가할 부분 chat_room table;
            MsgDBHelper mdbHelper = new MsgDBHelper(getApplicationContext());
            mdbHelper.insertRoom(roomNo, relation);

            if(type==1){
                msgDBHelper.insertMsg(sender, relation, relation, type, msg, post_msg, time, 1, 1);
            } else if(type==2){
                msgDBHelper.insertMsg(sender, relation, relation, type, msg, msg, time, 1, 1);
            }

            // 새로운 메시지가 추가됐음을 알리기 위한 Broadcast
            // 이 Broadcast를 받아서 채팅방의 순서를 재정렬함
            // MainChatFragment
            Intent intent = new Intent();
            intent.setAction("com.together.broadcast.room.integer");
            intent.putExtra("reload", 1);
            sendBroadcast(intent);

            // 새로운 메시지가 추가됐음을 알리기 위한 Broadcast
            // 이 Broadcast를 받아서 새로 온 메시지를 리스트에 추가함
            // InChattingActivity
            Intent intent2 = new Intent();
            intent2.setAction("com.together.broadcast.chat.integer");
            intent2.putExtra("plus", 1);
            intent2.putExtra("type", type);
            intent2.putExtra("msg", post_msg);
            intent2.putExtra("dis1", receiver);
            intent2.putExtra("dis2", receiver2);
            intent2.putExtra("Receiver", sender);
            intent2.putStringArrayListExtra("ReceiverArray", receiver_array);
            sendBroadcast(intent2);

            // 현재 보여주고 있는 최상위 Activity가 뭔지 출력해주는 부분/ 채팅방에 들어가있는 상태가 아니면 노티 띄워줌
            // com.together.linkalk.InChattingActivity
            ActivityManager am = (ActivityManager) mContext.getSystemService(ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);
            Log.d("topActivity", "CURRENT Activity ::" + taskInfo.get(0).topActivity.getClassName());
            // 방 번호 찾는 쿼리
            String query = "SELECT roomNo FROM chat_room WHERE relation='"+receiver+"'";
            SQLiteDatabase db = msgDBHelper.getReadableDatabase();
            Cursor c = db.rawQuery(query, null);
            int rn = 0;
            if(c.moveToFirst()){
                rn =  c.getInt(c.getColumnIndex("roomNo"));
            }
            if(!taskInfo.get(0).topActivity.getClassName().equals("com.together.linkalk.InChattingActivity")){
                NotificationManager notificationManager= (NotificationManager)mContext.getSystemService(NOTIFICATION_SERVICE);
                Intent it = new Intent(mContext, InChattingActivity.class);
                it.putStringArrayListExtra("Receiver", receiver_array);
                Notification.Builder builder = new Notification.Builder(mContext);
                PendingIntent pendingIntent = PendingIntent.getActivity(mContext, (int)System.currentTimeMillis()/1000, it, PendingIntent.FLAG_UPDATE_CURRENT);
                builder.setSmallIcon(R.mipmap.ic_launcher_round)
                        .setTicker("Linkalk")
                        .setWhen(System.currentTimeMillis())
                        .setContentTitle(sender).setContentText(post_msg)
                        .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setOngoing(false);
                notificationManager.notify(rn, builder.build());
            }
            c.close();
            db.close();
        }
    }   // 상대방이 먼저 대화 건 채팅방 생성 Asynctask

    // 혹시 바꼈을 지 모르는 Token 값을 서버에 업데이트 하는 Asynctask
    class UpdateMyToken extends AsyncTask<Void, Void, Void> {

        JSONObject object = new JSONObject();
        String sessionID;
        String token;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            SharedPreferences sharedPreferences = getSharedPreferences("maintain", Context.MODE_PRIVATE);
            sessionID = sharedPreferences.getString("sessionID", "");

            try {
                object.put("token", FirebaseInstanceId.getInstance().getToken());
            } catch (JSONException e) {
                e.printStackTrace();
            }

            token = object.toString();
        } // onPreExecute 끝

        @Override
        protected Void doInBackground(Void... params) {
            try{
                // 서버에 개인 프로필을 업데이트하기 위해 요청하는 부분
                URL url = new URL("http://www.o-ddang.com/linkalk/updateMyToken.php");
                HttpURLConnection httpURLConnection = (HttpURLConnection)url.openConnection();

                httpURLConnection.setDefaultUseCaches(false);
                httpURLConnection.setDoInput(true);
                httpURLConnection.setDoOutput(true);
                httpURLConnection.setRequestMethod("POST");

                httpURLConnection.setInstanceFollowRedirects( false );
                if(!TextUtils.isEmpty(sessionID)) {
                    httpURLConnection.setRequestProperty( "cookie", sessionID) ;
                }

                httpURLConnection.setRequestProperty("Accept", "application/json");
                httpURLConnection.setRequestProperty("Content-type", "application/json");

                OutputStream os = httpURLConnection.getOutputStream();
                os.write(token.getBytes());
                os.flush();

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (ProtocolException e) {
                e.printStackTrace();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }   // onDoing 끝
    }   // My Token Update 끝

    // 서버 연결 끊겨서 못 받았던 메시지 받아오는 쓰레드
//    class GetTmpMessage extends Thread{
//        Context mContext;
//
//        JSONObject object = new JSONObject();
//        String sessionID;
//        String mynickname;
//
//        String getTmpMsg;
//
//        Handler handler;
//
//        public GetTmpMessage(Context context){
//            mContext = context;
//            handler = new Handler();
//        }
//
//        public void run(){
//            SharedPreferences sharedPreferences = getSharedPreferences("maintain", Context.MODE_PRIVATE);
//            sessionID = sharedPreferences.getString("sessionID", "");
//            mynickname = sharedPreferences.getString("nickname", "");
//
//            try{
//                String getData = "1";
//                try {
//                    object.put("request", getData);
//                    object.put("nickname", mynickname);
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
//
//                URL url = new URL("http://www.o-ddang.com/linkalk/getTmpMsg.php");
//                HttpURLConnection httpURLConnection = (HttpURLConnection)url.openConnection();
//
//                httpURLConnection.setReadTimeout(5000);
//                httpURLConnection.setConnectTimeout(5000);
//                httpURLConnection.setDefaultUseCaches(false);
//                httpURLConnection.setDoInput(true);
//                httpURLConnection.setDoOutput(true);
//                httpURLConnection.setRequestMethod("POST");
//
//                httpURLConnection.setInstanceFollowRedirects( false );
//                if(!TextUtils.isEmpty(sessionID)) {
//                    httpURLConnection.setRequestProperty( "cookie", sessionID) ;
//                }
//
//                httpURLConnection.setRequestProperty("Accept", "application/json");
//                httpURLConnection.setRequestProperty("Content-type", "application/json");
//
//                OutputStream os = httpURLConnection.getOutputStream();
//                os.write(object.toString().getBytes());
//                os.flush();
//
//                // 서버 리턴
//                int responseStatusCode = httpURLConnection.getResponseCode();
//                Log.d("responseStatusCode", "response code - " + responseStatusCode);
//
//                InputStream inputStream;
//                if(responseStatusCode == HttpURLConnection.HTTP_OK) {
//                    inputStream = httpURLConnection.getInputStream();
//                } else{
//                    inputStream = httpURLConnection.getErrorStream();
//                }
//                InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
//                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
//
//                StringBuilder sb = new StringBuilder();
//                String line;
//
//                while((line = bufferedReader.readLine()) != null){
//                    sb.append(line);
//                }
//                bufferedReader.close();
//                getTmpMsg =  sb.toString().trim();
//
//                handler.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        int n;
//
//                        if(!TextUtils.isEmpty(getTmpMsg)){
//                            JSONObject object1 = null;
//                            try {
//                                object1 = new JSONObject(getTmpMsg);
//                                JSONArray array = object1.getJSONArray("tmpmsg");
//                                for(n=0; n<array.length(); n++) {
//                                    String s_obj = array.getString(n);
//                                    JSONObject obj2 = new JSONObject(s_obj);
//
//                                    String msgJson = obj2.getString("message");
//                                    JSONObject obj3 = new JSONObject(msgJson);
//                                    JSONArray ar = obj3.getJSONArray("receiver");
//                                    time = obj2.getString("time");
//                                    sender = obj3.getString("sender");
//                                    msg = obj3.getString("msg");
//                                    lan = obj3.getString("language");
//                                    receiver = "";
//                                    receiver2 = "";
//                                    receiver_array = new ArrayList<String>();
//
//                                    for(int i=0; i<ar.length(); i++){
//                                        receiver_array.add(ar.getString(i));
//                                        if(i==(ar.length()-1)){
//                                            receiver = receiver+ ar.getString(i);
//                                        } else {
//                                            receiver = receiver+ ar.getString(i)+"/";
//                                        }
//                                        if((ar.length()-1-i)==0){
//                                            receiver2 = receiver2 + ar.getString(ar.length()-1-i);
//                                        } else {
//                                            receiver2 = receiver2 + ar.getString(ar.length()-1-i) + "/";
//                                        }
//                                    }
//                                    msgDBHelper = new MsgDBHelper(mContext);
//
//                                    returnTransMsg rtm = new returnTransMsg(mContext);
//                                    rtm.execute(sender, msg, lan);
//                                }
//                            } catch (JSONException e) {
//                                e.printStackTrace();
//                            }
//                        }
//                    }
//                });
//            } catch (UnsupportedEncodingException e) {
//                e.printStackTrace();
//            } catch (ProtocolException e) {
//                e.printStackTrace();
//            } catch (MalformedURLException e) {
//                e.printStackTrace();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//    }

    // 소켓 연결이 끊겨서 받지 못했던 메시지를 받아오는 Asynctask
    class GetTmpMsg extends AsyncTask<Void, Void, String> {

        Context mContext;

        JSONObject object = new JSONObject();
        String sessionID;
        String mynickname;

        public GetTmpMsg(Context context){
            mContext = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            SharedPreferences sharedPreferences = getSharedPreferences("maintain", Context.MODE_PRIVATE);
            sessionID = sharedPreferences.getString("sessionID", "");
            mynickname = sharedPreferences.getString("nickname", "");

        } // onPreExecute 끝

        @Override
        protected String doInBackground(Void... params) {
            try{
                String getData = "1";
                try {
                    object.put("request", getData);
                    object.put("nickname", mynickname);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                URL url = new URL("http://www.o-ddang.com/linkalk/getTmpMsg.php");
                HttpURLConnection httpURLConnection = (HttpURLConnection)url.openConnection();

                httpURLConnection.setReadTimeout(5000);
                httpURLConnection.setConnectTimeout(5000);
                httpURLConnection.setDefaultUseCaches(false);
                httpURLConnection.setDoInput(true);
                httpURLConnection.setDoOutput(true);
                httpURLConnection.setRequestMethod("POST");

                httpURLConnection.setInstanceFollowRedirects( false );
                if(!TextUtils.isEmpty(sessionID)) {
                    httpURLConnection.setRequestProperty( "cookie", sessionID) ;
                }

                httpURLConnection.setRequestProperty("Accept", "application/json");
                httpURLConnection.setRequestProperty("Content-type", "application/json");

                OutputStream os = httpURLConnection.getOutputStream();
                os.write(object.toString().getBytes());
                os.flush();

                // 서버 리턴
                int responseStatusCode = httpURLConnection.getResponseCode();
                Log.d("responseStatusCode", "response code - " + responseStatusCode);

                InputStream inputStream;
                if(responseStatusCode == HttpURLConnection.HTTP_OK) {
                    inputStream = httpURLConnection.getInputStream();
                } else{
                    inputStream = httpURLConnection.getErrorStream();
                }
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

                StringBuilder sb = new StringBuilder();
                String line;

                while((line = bufferedReader.readLine()) != null){
                    sb.append(line);
                }
                bufferedReader.close();
                return sb.toString().trim();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return e.getMessage();
            } catch (ProtocolException e) {
                e.printStackTrace();
                return e.getMessage();
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return e.getMessage();
            } catch (IOException e) {
                e.printStackTrace();
                return e.getMessage();
            }
        }   // onDoing 끝

        @Override
        protected void onPostExecute(final String s) {
            super.onPostExecute(s);
            try {
                int n;

                if(!TextUtils.isEmpty(s)){
                    JSONObject object1 = new JSONObject(s);
                    JSONArray array = object1.getJSONArray("tmpmsg");

                    for(n=0; n<array.length(); n++) {
                        String s_obj = array.getString(n);
                        JSONObject obj2 = new JSONObject(s_obj);

                        String msgJson = obj2.getString("message");
                        time = obj2.getString("time");
                        JSONObject obj3 = new JSONObject(msgJson);
                        sender = obj3.getString("sender");
                        type = obj3.getInt("type");
                        msg = obj3.getString("msg");
                        lan = obj3.getString("language");
                        receiver = "";
                        receiver2 = "";
                        receiver_array = new ArrayList<String>();

                        // 친구 아닌사람이 보낸 메시지면 사진 가져오는 async
                        GetNotFriendPic gnfp = new GetNotFriendPic(getApplicationContext());
                        gnfp.execute(sender);

                        JSONArray ar = obj3.getJSONArray("receiver");
                        for(int i=0; i<ar.length(); i++){
                            receiver_array.add(ar.getString(i));
                            if(i==(ar.length()-1)){
                                receiver = receiver+ ar.getString(i);
                            } else {
                                receiver = receiver+ ar.getString(i)+"/";
                            }
                            if((ar.length()-1-i)==0){
                                receiver2 = receiver2 + ar.getString(ar.length()-1-i);
                            } else {
                                receiver2 = receiver2 + ar.getString(ar.length()-1-i) + "/";
                            }
                        }
                        msgDBHelper = new MsgDBHelper(mContext);

                        returnTransMsg rtm = new returnTransMsg(mContext, sender, receiver, type, msg, lan);
                        rtm.execute();
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
                System.out.println("GetTmpMsgJSONError : " + e.getMessage());
            }
        }
    }   // GetTmpMsg 끝

    // 메시지 번역해서 받아오는 쓰레드
//    class returnTransMsgg extends Thread{
//        String clientId = "lIIpx5B_n000ent6_E8X";
//        String clientSecret = "m0OA6GEKuH";
//
//        SharedPreferences sharedPreferences = getSharedPreferences("maintain", MODE_PRIVATE);
//        String mLan = sharedPreferences.getString("language", "");
//
//        Context mContext;
//
//        returnTransMsgg(Context context, String message, String language) {
//            mContext = context;
//            pre_msg = message;
//            oLan = language;
//        }
//
//        public void run(){
//            if(!my_nickname.equals(sender)){
//                if((mLan.equals("Korean")&&oLan.equals("Chinese")) || (mLan.equals("Chinese")&&oLan.equals("Korean")) || (mLan.equals("Korean")&&oLan.equals("English")) || (mLan.equals("English")&&oLan.equals("Korean"))){
//                    try{
//                        String apiUrl = "https://openapi.naver.com/v1/papago/n2mt";
//                        URL url = new URL(apiUrl);
//                        HttpURLConnection con = (HttpURLConnection)url.openConnection();
//                        con.setRequestMethod("POST");
//                        con.setRequestProperty("X-Naver-Client-Id", clientId);
//                        con.setRequestProperty("X-Naver-Client-Secret", clientSecret);
//                        if(mLan.equals("Korean")&&oLan.equals("Chinese")){
//                            postParams = "source=zh-CN&target=ko&text=" + pre_msg;
//                        } else if (mLan.equals("Chinese")&&oLan.equals("Korean")){
//                            postParams = "source=ko&target=zh-CN&text=" + pre_msg;
//                        } else if (mLan.equals("Korean")&&oLan.equals("English")){
//                            postParams = "source=en&target=ko&text=" + pre_msg;
//                        } else if (mLan.equals("English")&&oLan.equals("Korean")){
//                            postParams = "source=ko&target=en&text=" + pre_msg;
//                        }
//                        con.setDoOutput(true);
//                        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
//                        wr.writeBytes(postParams);
//                        wr.flush();
//                        wr.close();
//
//                        // 서버 응답 - 번역 메시지
//                        int responseCode = con.getResponseCode();
//                        BufferedReader br;
//                        if(responseCode==200) { // 정상 호출
//                            br = new BufferedReader(new InputStreamReader(con.getInputStream()));
//                        } else {  // 에러 발생
//                            br = new BufferedReader(new InputStreamReader(con.getErrorStream()));
//                        }
//                        String inputLine;
//                        StringBuffer response = new StringBuffer();
//                        while ((inputLine = br.readLine()) != null) {
//                            response.append(inputLine);
//                        }
//                        br.close();
//                        post_msg = response.toString();
//                    } catch (Exception e){
//                        System.out.println("papagoErrorMsg : " + e);
//                    }
//                } else if((mLan.equals("English")&&oLan.equals("Chinese")) || (mLan.equals("Chinese")&&oLan.equals("English"))) {
//                    // 영어 또는 중국어를 먼저 한국어로 변경
//                    try{
//                        String apiUrl = "https://openapi.naver.com/v1/papago/n2mt";
//                        URL url = new URL(apiUrl);
//                        HttpURLConnection con = (HttpURLConnection)url.openConnection();
//                        con.setRequestMethod("POST");
//                        con.setRequestProperty("X-Naver-Client-Id", clientId);
//                        con.setRequestProperty("X-Naver-Client-Secret", clientSecret);
//                        if(oLan.equals("Chinese")){
//                            postParams = "source=zh-CN&target=ko&text=" + pre_msg;
//                        } else if (oLan.equals("English")){
//                            postParams = "source=en&target=ko&text=" + pre_msg;
//                        }
//                        con.setDoOutput(true);
//                        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
//                        wr.writeBytes(postParams);
//                        wr.flush();
//                        wr.close();
//
//                        // 서버 응답 - 번역 메시지
//                        int responseCode = con.getResponseCode();
//                        BufferedReader br;
//                        if(responseCode==200) { // 정상 호출
//                            br = new BufferedReader(new InputStreamReader(con.getInputStream()));
//                        } else {  // 에러 발생
//                            br = new BufferedReader(new InputStreamReader(con.getErrorStream()));
//                        }
//                        String inputLine;
//                        StringBuffer response = new StringBuffer();
//                        while ((inputLine = br.readLine()) != null) {
//                            response.append(inputLine);
//                        }
//                        br.close();
//                        String postMsg_tmp = response.toString();
//
//                        try {
//                            JSONObject ob = new JSONObject(postMsg_tmp);
//                            JSONObject ob1 = new JSONObject(ob.getString("message"));
//                            JSONObject ob2 = new JSONObject(ob1.getString("result"));
//                            postMsg_tmp = ob2.getString("translatedText");
//                        } catch (JSONException e) {
//                            e.printStackTrace();
//                        }
//
//                        // 한국어로 받은 메시지를 다시 타겟에 맞게 중국어 또는 영어로 변경
//                        apiUrl = "https://openapi.naver.com/v1/papago/n2mt";
//                        url = new URL(apiUrl);
//                        con = (HttpURLConnection)url.openConnection();
//                        con.setRequestMethod("POST");
//                        con.setRequestProperty("X-Naver-Client-Id", clientId);
//                        con.setRequestProperty("X-Naver-Client-Secret", clientSecret);
//                        if(mLan.equals("English")){
//                            postParams = "source=ko&target=en&text=" + postMsg_tmp;
//                        } else if (mLan.equals("Chinese")){
//                            postParams = "source=ko&target=zh-CN&text=" + postMsg_tmp;
//                        }
//                        con.setDoOutput(true);
//                        wr = new DataOutputStream(con.getOutputStream());
//                        wr.writeBytes(postParams);
//                        wr.flush();
//                        wr.close();
//
//                        // 서버 응답 - 번역 메시지
//                        responseCode = con.getResponseCode();
//                        if(responseCode==200) { // 정상 호출
//                            br = new BufferedReader(new InputStreamReader(con.getInputStream()));
//                        } else {  // 에러 발생
//                            br = new BufferedReader(new InputStreamReader(con.getErrorStream()));
//                        }
//                        response = new StringBuffer();
//                        while ((inputLine = br.readLine()) != null) {
//                            response.append(inputLine);
//                        }
//                        br.close();
//                        post_msg = response.toString();
//                    } catch (Exception e){
//                        System.out.println(e);
//                    }
//                } else if((mLan.equals("Korean")&&oLan.equals("Japanese")) || (mLan.equals("Japanese")&&oLan.equals("Korean"))){
//                    try{
//                        String apiURL = "https://openapi.naver.com/v1/language/translate";
//                        URL url = new URL(apiURL);
//                        HttpURLConnection con = (HttpURLConnection)url.openConnection();
//                        con.setRequestMethod("POST");
//                        con.setRequestProperty("X-Naver-Client-Id", clientId);
//                        con.setRequestProperty("X-Naver-Client-Secret", clientSecret);
//                        // post request
//                        if(mLan.equals("Korean")&&oLan.equals("Japanese")){
//                            postParams = "source=ja&target=ko&text=" + pre_msg;
//                        } else if (mLan.equals("Japanese")&&oLan.equals("Korean")){
//                            postParams = "source=ko&target=ja&text=" + pre_msg;
//                        }
//                        con.setDoOutput(true);
//                        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
//                        wr.writeBytes(postParams);
//                        wr.flush();
//                        wr.close();
//
//                        // 서버 응답 - 번역 메시지
//                        int responseCode = con.getResponseCode();
//                        BufferedReader br;
//                        if(responseCode==200) { // 정상 호출
//                            br = new BufferedReader(new InputStreamReader(con.getInputStream()));
//                        } else {  // 에러 발생
//                            br = new BufferedReader(new InputStreamReader(con.getErrorStream()));
//                        }
//                        String inputLine;
//                        StringBuffer response = new StringBuffer();
//                        while ((inputLine = br.readLine()) != null) {
//                            response.append(inputLine);
//                        }
//                        br.close();
//                        post_msg = response.toString();
//                    } catch (Exception e){
//                        System.out.println(e);
//                    }
//                } else if ((mLan.equals("Chinese")&&oLan.equals("Japanese")) || (mLan.equals("Japanese")&&oLan.equals("Chinese"))) {
//                    try{
//                        String apiURL = "";
//                        URL url = null;
//                        HttpURLConnection con = null;
//                        // post request
//                        if(oLan.equals("Japanese")){
//                            apiURL = "https://openapi.naver.com/v1/language/translate";
//                            postParams = "source=ja&target=ko&text=" + pre_msg;
//                        } else if (oLan.equals("Chinese")){
//                            apiURL = "https://openapi.naver.com/v1/papago/n2mt";
//                            postParams = "source=zh-CN&target=ko&text=" + pre_msg;
//                        }
//                        url = new URL(apiURL);
//                        con = (HttpURLConnection)url.openConnection();
//                        con.setRequestMethod("POST");
//                        con.setRequestProperty("X-Naver-Client-Id", clientId);
//                        con.setRequestProperty("X-Naver-Client-Secret", clientSecret);
//                        con.setDoOutput(true);
//                        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
//                        wr.writeBytes(postParams);
//                        wr.flush();
//                        wr.close();
//
//                        // 서버 응답 - 번역 메시지
//                        int responseCode = con.getResponseCode();
//                        BufferedReader br;
//                        if(responseCode==200) { // 정상 호출
//                            br = new BufferedReader(new InputStreamReader(con.getInputStream()));
//                        } else {  // 에러 발생
//                            br = new BufferedReader(new InputStreamReader(con.getErrorStream()));
//                        }
//                        String inputLine;
//                        StringBuffer response = new StringBuffer();
//                        while ((inputLine = br.readLine()) != null) {
//                            response.append(inputLine);
//                        }
//                        br.close();
//                        String postMsg_tmp = response.toString();
//
//                        try {
//                            JSONObject ob = new JSONObject(postMsg_tmp);
//                            JSONObject ob1 = new JSONObject(ob.getString("message"));
//                            JSONObject ob2 = new JSONObject(ob1.getString("result"));
//                            postMsg_tmp = ob2.getString("translatedText");
//                        } catch (JSONException e) {
//                            e.printStackTrace();
//                        }
//
//                        // 한국어로 받은 메시지를 다시 타겟에 맞게 중국어 또는 영어로 변경
//                        // post request
//                        if(mLan.equals("Chinese")){
//                            apiURL = "https://openapi.naver.com/v1/papago/n2mt";
//                            postParams = "source=ko&target=zh-CN&text=" + postMsg_tmp;
//                        } else if (mLan.equals("Japanese")){
//                            apiURL = "https://openapi.naver.com/v1/language/translate";
//                            postParams = "source=ko&target=ja&text=" + postMsg_tmp;
//                        }
//                        url = new URL(apiURL);
//                        con = (HttpURLConnection)url.openConnection();
//                        con.setRequestMethod("POST");
//                        con.setRequestProperty("X-Naver-Client-Id", clientId);
//                        con.setRequestProperty("X-Naver-Client-Secret", clientSecret);
//                        con.setDoOutput(true);
//                        wr = new DataOutputStream(con.getOutputStream());
//                        wr.writeBytes(postParams);
//                        wr.flush();
//                        wr.close();
//
//                        // 서버 응답 - 번역 메시지
//                        responseCode = con.getResponseCode();
//                        if(responseCode==200) { // 정상 호출
//                            br = new BufferedReader(new InputStreamReader(con.getInputStream()));
//                        } else {  // 에러 발생
//                            br = new BufferedReader(new InputStreamReader(con.getErrorStream()));
//                        }
//                        response = new StringBuffer();
//                        while ((inputLine = br.readLine()) != null) {
//                            response.append(inputLine);
//                        }
//                        br.close();
//                        post_msg = response.toString();
//                    } catch (Exception e){
//                        System.out.println(e);
//                    }
//                } else if ((mLan.equals("Japanese")&&oLan.equals("English")) || (mLan.equals("English")&&oLan.equals("Japanese"))) {
//                    try{
//                        String apiURL = "";
//                        URL url = null;
//                        HttpURLConnection con = null;
//                        // post request
//                        if(oLan.equals("English")){
//                            apiURL = "https://openapi.naver.com/v1/papago/n2mt";
//                            postParams = "source=en&target=ko&text=" + pre_msg;
//                        } else if (oLan.equals("Japanese")){
//                            apiURL = "https://openapi.naver.com/v1/language/translate";
//                            postParams = "source=jaN&target=ko&text=" + pre_msg;
//                        }
//                        url = new URL(apiURL);
//                        con = (HttpURLConnection)url.openConnection();
//                        con.setRequestMethod("POST");
//                        con.setRequestProperty("X-Naver-Client-Id", clientId);
//                        con.setRequestProperty("X-Naver-Client-Secret", clientSecret);
//                        con.setDoOutput(true);
//                        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
//                        wr.writeBytes(postParams);
//                        wr.flush();
//                        wr.close();
//
//                        // 서버 응답 - 번역 메시지
//                        int responseCode = con.getResponseCode();
//                        BufferedReader br;
//                        if(responseCode==200) { // 정상 호출
//                            br = new BufferedReader(new InputStreamReader(con.getInputStream()));
//                        } else {  // 에러 발생
//                            br = new BufferedReader(new InputStreamReader(con.getErrorStream()));
//                        }
//                        String inputLine;
//                        StringBuffer response = new StringBuffer();
//                        while ((inputLine = br.readLine()) != null) {
//                            response.append(inputLine);
//                        }
//                        br.close();
//                        String postMsg_tmp = response.toString();
//
//                        try {
//                            JSONObject ob = new JSONObject(postMsg_tmp);
//                            JSONObject ob1 = new JSONObject(ob.getString("message"));
//                            JSONObject ob2 = new JSONObject(ob1.getString("result"));
//                            postMsg_tmp = ob2.getString("translatedText");
//                        } catch (JSONException e) {
//                            e.printStackTrace();
//                        }
//
//                        // 한국어로 받은 메시지를 다시 타겟에 맞게 중국어 또는 영어로 변경
//                        // post request
//                        if(mLan.equals("Japanese")){
//                            apiURL = "https://openapi.naver.com/v1/language/translate";
//                            postParams = "source=ko&target=ja&text=" + postMsg_tmp;
//                        } else if (mLan.equals("English")){
//                            apiURL = "https://openapi.naver.com/v1/papago/n2mt";
//                            postParams = "source=ko&target=en&text=" + postMsg_tmp;
//                        }
//                        url = new URL(apiURL);
//                        con = (HttpURLConnection)url.openConnection();
//                        con.setRequestMethod("POST");
//                        con.setRequestProperty("X-Naver-Client-Id", clientId);
//                        con.setRequestProperty("X-Naver-Client-Secret", clientSecret);
//                        con.setDoOutput(true);
//                        wr = new DataOutputStream(con.getOutputStream());
//                        wr.writeBytes(postParams);
//                        wr.flush();
//                        wr.close();
//
//                        // 서버 응답 - 번역 메시지
//                        responseCode = con.getResponseCode();
//                        if(responseCode==200) { // 정상 호출
//                            br = new BufferedReader(new InputStreamReader(con.getInputStream()));
//                        } else {  // 에러 발생
//                            br = new BufferedReader(new InputStreamReader(con.getErrorStream()));
//                        }
//                        response = new StringBuffer();
//                        while ((inputLine = br.readLine()) != null) {
//                            response.append(inputLine);
//                        }
//                        br.close();
//                        post_msg = response.toString();
//                    } catch (Exception e){
//                        System.out.println(e);
//                    }
//                }
//
//                System.out.println("TranslateResult : "+ post_msg);
//                try {
//                    JSONObject ob = new JSONObject(post_msg);
//                    JSONObject ob1 = new JSONObject(ob.getString("message"));
//                    JSONObject ob2 = new JSONObject(ob1.getString("result"));
//                    post_msg = ob2.getString("translatedText");
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
//            } else {
//                post_msg = pre_msg;
//            }
//
//            msgDBHelper = new MsgDBHelper(mContext);
//
//            // 메시지가 왔는데 기존에 없는 방일 경우
//            String checkExistRoom = "SELECT * FROM chat_room WHERE relation = '"+dis1+"' or relation = '"+dis2+"';";
//            SQLiteDatabase db = msgDBHelper.getReadableDatabase();
//            Cursor c = db.rawQuery(checkExistRoom, null);
//            int existRoom = c.getCount();
//            if(existRoom==0){
//                JSONObject jsonObject = new JSONObject();
//                try {
//                    jsonObject.put("sender", sender);
//                    jsonObject.put("receiver", receiver);
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
//
//                // 채팅방 DB 생성하기
//                GetChatRoom gcr = new GetChatRoom();
//                gcr.execute(jsonObject.toString());
//            } else {
//                msgDBHelper.insertMsg(dis1, dis2, sender, msg, post_msg, time, 1, 1);
//
//                // 새로운 메시지가 추가됐음을 알리기 위한 Broadcast
//                // 이 Broadcast를 받아서 채팅방의 순서를 재정렬함
//                // MainChatFragment
//                Intent intent = new Intent();
//                intent.setAction("com.together.broadcast.room.integer");
//                intent.putExtra("reload", 1);
//                sendBroadcast(intent);
//            }
//
//            // 새로운 메시지가 추가됐음을 알리기 위한 Broadcast
//            // 이 Broadcast를 받아서 새로 온 메시지를 리스트에 추가함
//            // InChattingActivity
//            Intent intent2 = new Intent();
//            intent2.setAction("com.together.broadcast.chat.integer");
//            intent2.putExtra("plus", 1);
//            intent2.putExtra("msg", post_msg);
//            intent2.putExtra("Receiver", sender);
//            sendBroadcast(intent2);
//
//            // 현재 보여주고 있는 최상위 Activity가 뭔지 출력해주는 부분/ 채팅방에 들어가있는 상태가 아니면 노티 띄워줌
//            // com.together.linkalk.InChattingActivity
//            ActivityManager am = (ActivityManager) mContext.getSystemService(ACTIVITY_SERVICE);
//            List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);
//            Log.d("topActivity", "CURRENT Activity ::" + taskInfo.get(0).topActivity.getClassName());
//            // 방 번호 찾는 쿼리
//            String query = "SELECT roomNo FROM chat_room WHERE relation='"+dis1+"' OR relation='"+dis2+"'";
//            db = msgDBHelper.getReadableDatabase();
//            c = db.rawQuery(query, null);
//            int rn = 0;
//            if(c.moveToFirst()){
//                rn =  c.getInt(c.getColumnIndex("roomNo"));
//            }
//            if(!taskInfo.get(0).topActivity.getClassName().equals("com.together.linkalk.InChattingActivity")){
//                NotificationManager notificationManager= (NotificationManager)mContext.getSystemService(NOTIFICATION_SERVICE);
//                Intent intent = new Intent(mContext, InChattingActivity.class);
//                intent.putExtra("Receiver", sender);
//                Notification.Builder builder = new Notification.Builder(mContext);
//                PendingIntent pendingIntent = PendingIntent.getActivity(mContext, (int)System.currentTimeMillis()/1000, intent, PendingIntent.FLAG_UPDATE_CURRENT);
//                builder.setSmallIcon(R.mipmap.ic_launcher_round)
//                        .setTicker("Linkalk")
//                        .setWhen(System.currentTimeMillis())
//                        .setContentTitle(sender).setContentText(post_msg)
//                        .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
//                        .setContentIntent(pendingIntent)
//                        .setAutoCancel(true)
//                        .setOngoing(false);
//                notificationManager.notify(rn, builder.build());
//            }
//            c.close();
//            db.close();
//
//        }   // run 끝
//
//    }   // 메시지 번역 Thread 끝

    // 메시지 번역한 결과를 받아오는 Asynctask
    class returnTransMsg extends AsyncTask<Void, Void, String> {

        Context mContext;
        String oNick;
        int typee;
        String beforemsg;
        String oLan;
        String receive;


        returnTransMsg(Context context, String sender, String rec, int ty, String msg, String language){
            mContext = context;
            oNick = sender;
            typee = ty;
            beforemsg = msg;
            oLan = language;
            receive = rec;
        }

        @Override
        protected String doInBackground(Void... params) {
            try{
                SharedPreferences sharedPreferences = getSharedPreferences("maintain", MODE_PRIVATE);
                String mNick = sharedPreferences.getString("nickname", "");
                String mLan = sharedPreferences.getString("language", "");

                if(mNick.equals(oNick)){
                    return beforemsg;
                } else {
                    JSONObject obt = new JSONObject();
                    obt.put("msg", beforemsg);
                    obt.put("my_language", mLan);
                    obt.put("other_language", oLan);

                    String send = obt.toString();

                    // 서버와 연결하기 위해 세션 아이디 불러와서 커넥트
                    String sessionID = sharedPreferences.getString("sessionID", "");

                    Log.i("sessionID",sessionID);

                    URL url = new URL("http://www.o-ddang.com/linkalk/returnTransMsg.php");
                    HttpURLConnection httpURLConnection = (HttpURLConnection)url.openConnection();

                    httpURLConnection.setReadTimeout(5000);
                    httpURLConnection.setConnectTimeout(5000);
                    httpURLConnection.setDefaultUseCaches(false);
                    httpURLConnection.setDoInput(true);
                    httpURLConnection.setDoOutput(true);
                    httpURLConnection.setRequestMethod("POST");

                    httpURLConnection.setInstanceFollowRedirects( false );
                    if(!TextUtils.isEmpty(sessionID)) {
                        httpURLConnection.setRequestProperty( "cookie", sessionID) ;
                    }

                    httpURLConnection.setRequestProperty("Accept", "application/json");
                    httpURLConnection.setRequestProperty("Content-type", "application/json");

                    OutputStream os = httpURLConnection.getOutputStream();
                    os.write(send.getBytes());
                    os.flush();

                    // 서버에서 번역된 결과 메시지를 리턴
                    int responseStatusCode = httpURLConnection.getResponseCode();
                    Log.d("responseStatusCode", "response code - " + responseStatusCode);

                    InputStream inputStream;
                    if(responseStatusCode == HttpURLConnection.HTTP_OK) {
                        inputStream = httpURLConnection.getInputStream();
                    } else{
                        inputStream = httpURLConnection.getErrorStream();
                    }
                    InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

                    StringBuilder sb = new StringBuilder();
                    String line;

                    while((line = bufferedReader.readLine()) != null){
                        sb.append(line);
                    }
                    bufferedReader.close();
                    return sb.toString().trim();
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return e.getMessage();
            } catch (ProtocolException e) {
                e.printStackTrace();
                return e.getMessage();
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return e.getMessage();
            } catch (IOException e) {
                e.printStackTrace();
                return e.getMessage();
            } catch (JSONException e) {
                e.printStackTrace();
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            System.out.println("TranslateResult : "+ s);
            try {
                JSONObject ob = new JSONObject(s);
                JSONObject ob1 = new JSONObject(ob.getString("message"));
                JSONObject ob2 = new JSONObject(ob1.getString("result"));
                post_msg = ob2.getString("translatedText");
            } catch (JSONException e) {
                e.printStackTrace();
                post_msg = s;
            }

            msgDBHelper = new MsgDBHelper(mContext);

            // 메시지가 왔는데 기존에 있는 방인지 확인
            String checkExistRoom = "SELECT * FROM chat_room WHERE relation = '"+receive+"' OR relation='"+receiver2+"';";
            SQLiteDatabase db = msgDBHelper.getReadableDatabase();
            Cursor c = db.rawQuery(checkExistRoom, null);
            int existRoom = c.getCount();
            if(existRoom==0){
                // 방이 없으면
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("sender", sender);
                    jsonObject.put("receiver", new JSONArray(receiver_array));
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                // 채팅방 DB 생성하기
                GetChatRoom gcr = new GetChatRoom(mContext);
                gcr.execute(jsonObject.toString());
            } else {
                // 방이 있으면
                msgDBHelper.insertMsg(sender, receive, receiver2, type, beforemsg, post_msg, time, 1, 1);

                // 새로운 메시지가 추가됐음을 알리기 위한 Broadcast
                // 이 Broadcast를 받아서 채팅방의 순서를 재정렬함
                // MainChatFragment
                Intent intent = new Intent();
                intent.setAction("com.together.broadcast.room.integer");
                intent.putExtra("reload", 1);
                sendBroadcast(intent);

                // 새로운 메시지가 추가됐음을 알리기 위한 Broadcast
                // 이 Broadcast를 받아서 새로 온 메시지를 리스트에 추가함
                // InChattingActivity
                Intent intent2 = new Intent();
                intent2.setAction("com.together.broadcast.chat.integer");
                intent2.putExtra("plus", 1);
                intent2.putExtra("type", type);
                intent2.putExtra("msg", post_msg);
                intent2.putExtra("dis1", receive);
                intent2.putExtra("dis2", receiver2);
                intent2.putExtra("Receiver", sender);
                intent2.putStringArrayListExtra("ReceiverArray", receiver_array);
                sendBroadcast(intent2);

                // 현재 보여주고 있는 최상위 Activity가 뭔지 출력해주는 부분/ 채팅방에 들어가있는 상태가 아니면 노티 띄워줌
                // com.together.linkalk.InChattingActivity
                ActivityManager am = (ActivityManager) mContext.getSystemService(ACTIVITY_SERVICE);
                List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);
                Log.d("topActivity", "CURRENT Activity ::" + taskInfo.get(0).topActivity.getClassName());
                // 방 번호 찾는 쿼리
                String query = "SELECT roomNo FROM chat_room WHERE relation='"+receive+"' OR relation='"+receiver2+"'";
                db = msgDBHelper.getReadableDatabase();
                c = db.rawQuery(query, null);
                int rn = 0;
                if(c.moveToFirst()){
                    rn =  c.getInt(c.getColumnIndex("roomNo"));
                }
                if(!taskInfo.get(0).topActivity.getClassName().equals("com.together.linkalk.InChattingActivity")){
                    NotificationManager notificationManager= (NotificationManager)mContext.getSystemService(NOTIFICATION_SERVICE);
                    Intent intent3 = new Intent(mContext, InChattingActivity.class);
                    intent3.putStringArrayListExtra("Receiver", receiver_array);
                    Notification.Builder builder = new Notification.Builder(mContext);
                    PendingIntent pendingIntent = PendingIntent.getActivity(mContext, (int)System.currentTimeMillis()/1000, intent3, PendingIntent.FLAG_UPDATE_CURRENT);
                    builder.setSmallIcon(R.mipmap.ic_launcher_round)
                            .setTicker("Linkalk")
                            .setWhen(System.currentTimeMillis())
                            .setContentTitle(sender).setContentText(post_msg)
                            .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                            .setOngoing(false);
                    notificationManager.notify(rn, builder.build());
                }
                c.close();
                db.close();
            }
        }
    }   // 상대방이 먼저 대화 건 채팅방 생성 Asynctask

    // 친구 아닌 사람이 보낸 메시지를 받으면 그 사람의 프로필 사진을 가져오는 어싱크
    class GetNotFriendPic extends AsyncTask<String, Void, String>{
        Context mContext;
        String senderNickname;

        GetNotFriendPic(Context context){
            mContext = context;
        }

        @Override
        protected String doInBackground(String... params) {
            senderNickname = params[0];
            SharedPreferences sharedPreferences = mContext.getSharedPreferences("maintain", Context.MODE_PRIVATE);
            String sessionID = sharedPreferences.getString("sessionID", "");
            SharedPreferences shPreferences = mContext.getSharedPreferences("tmpFriendPicPath", Context.MODE_PRIVATE);

            if(!my_nickname.equals(senderNickname) && TextUtils.isEmpty(shPreferences.getString(senderNickname, ""))){
                SharedPreferences sp = mContext.getSharedPreferences("MyFriend", MODE_PRIVATE);
                int mfCount = 0;
                int difCount = 0;
                for(int i=0; ; i++){
                    if(sp.contains(String.valueOf(i))){
                        mfCount++;
                        try {
                            JSONObject ob = new JSONObject(sp.getString(String.valueOf(i), ""));
                            String nic = ob.getString("nickname");
                            if(nic.equals(senderNickname)){
                                break;
                            } else {
                              difCount++;
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {
                        break;
                    }
                }
                if(mfCount==difCount){
                    try {
                        URL url = new URL("http://www.o-ddang.com/linkalk/getNotFriendPic.php");
                        HttpURLConnection conn = (HttpURLConnection)url.openConnection();

                        conn.setReadTimeout(10000);
                        conn.setConnectTimeout(15000);
                        conn.setDefaultUseCaches(false);
                        conn.setDoInput(true);
                        conn.setDoOutput(true);
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("accept-charset", "UTF-8");
                        conn.setRequestProperty("content-type", "application/x-www-form-urlencoded; charset=utf-8");

                        conn.setInstanceFollowRedirects( false );
                        if(!TextUtils.isEmpty(sessionID)) {
                            conn.setRequestProperty( "cookie", sessionID) ;
                        }

                        String signal = URLEncoder.encode("nickname", "UTF-8")+"="+URLEncoder.encode(senderNickname, "UTF-8");
                        OutputStreamWriter osw = new OutputStreamWriter(conn.getOutputStream());
                        osw.write(signal);
                        osw.flush();
                        osw.close();

                        int serverResponseCode = conn.getResponseCode();
                        String serverResponseMessage = conn.getResponseMessage();

                        if(serverResponseCode == 200){

                        }

                        BufferedReader rd = null;

                        rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while((line = rd.readLine()) != null){
                            sb.append(line);
                        }
                        rd.close();
                        return sb.toString().trim();
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                        return e.getMessage();
                    } catch (IOException e) {
                        e.printStackTrace();
                        return e.getMessage();
                    }
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            if(!TextUtils.isEmpty(s)){
                try {
                    JSONObject jobject = new JSONObject(s);
                    String path = jobject.getString(senderNickname);

                    SharedPreferences sp = getSharedPreferences("tmpFriendPicPath", MODE_PRIVATE);
                    if(!sp.getString(senderNickname, "").equals(path)){
                        SharedPreferences.Editor sp_ed = sp.edit();
                        sp_ed.putString(String.valueOf(senderNickname), path);
                        sp_ed.commit();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
