package com.together.linkalk;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.bumptech.glide.Glide;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.MODE_PRIVATE;

/**
 * Created by kimhj on 2017-07-06.
 */

public class MainSettingFragment extends Fragment {

    private final static int PICT_CROP_CODE = 5555;
    private final static int CAMERA_CODE = 1111;
    private final static int GALLERY_CODE = 3333;
    private static Uri mUri;
    private static String mCurrentPhotoPath;

    Button member_pic_camera;
    Button member_pic_gallery;
    Button member_pic_del;
    FaceOverlayView member_pic;
    ImageView member_pic2;

    Button member_modify_button;


    public MainSettingFragment(){
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        RelativeLayout layout = (RelativeLayout)inflater.inflate(R.layout.main_setting_activity, container, false);

        Button.OnClickListener btnClickListener = new Button.OnClickListener(){
            @Override
            public void onClick(View v) {
                switch (v.getId()){
                    case R.id.member_modify_button:
                        // 핸드폰 네트워크 상태 체크
                        String status = NetWorkStatusCheck.getConnectivityStatusString(getActivity().getApplicationContext());
                        if(status.equals("WIFI") || status.equals("MOBILE")){
                            // 네트워크 상태가 연결되어 있으면, 서버에서 내 정보 받아와서 출력
                            GetMyProfile getMyProfile = new GetMyProfile();
                            getMyProfile.execute();
                        } else if(status.equals("NOT")){
                            SharedPreferences myProfilerShared = getActivity().getSharedPreferences("MyProfile", Context.MODE_PRIVATE);
                            SharedPreferences loggedMemberShared = getActivity().getSharedPreferences("maintain", Context.MODE_PRIVATE);

                            if(myProfilerShared.getString("nickname", "").equals(loggedMemberShared.getString("nickname", ""))){
                                // 네트워크 상태가 끊어져 있으면, 제일 마지막에 저장했던 내 정보를 불러오거나
                                Intent intent = new Intent(getActivity().getApplicationContext(), MyProfileModify.class);
                                startActivity(intent);
                            } else {
                                // 네트워크 상태가 끊어져 있으면, 제일 마지막에 저장했던 정보도 없을 경우 메시지 출력력
                                android.support.v7.app.AlertDialog.Builder alertDialogBuilder = new android.support.v7.app.AlertDialog.Builder(getActivity());
                                alertDialogBuilder.setMessage("네트워크 연결이 되어있지 않아 정보를 불러올 수 없습니다.")
                                        .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.cancel();
                                            }
                                        }).create().show();
                            }
                        }
                        break;
                    case R.id.member_pic_camera:
                        TakePicIntent();
                        break;
                    case R.id.member_pic_gallery:
                        GetPicIntent();
                        break;
                    case R.id.member_pic_del:
                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getContext());
                        final SharedPreferences sp = getActivity().getSharedPreferences("maintain", MODE_PRIVATE);
                        final SharedPreferences.Editor sp_editor = sp.edit();
                        if(!TextUtils.isEmpty(sp.getString("picPath", null))){
                            alertDialogBuilder
                                    .setTitle("사진 빼기")
                                    .setMessage("정말로 등록한 사진을 지우시겠습니까?")
                                    .setCancelable(true)
                                    .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            DeleteProfilePic dpp = new DeleteProfilePic();
                                            dpp.execute();

                                            sp_editor.putString("picPath", null);
                                            sp_editor.commit();
                                            Handler ha = new Handler();
                                            ha.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    BitmapDrawable drawable = (BitmapDrawable)getResources().getDrawable(R.drawable.baseprofileimg);
                                                    Bitmap bitmap = drawable.getBitmap();
                                                    member_pic2.setImageBitmap(bitmap);
                                                }
                                            });

                                            member_pic_del.setVisibility(View.GONE);
                                            dialog.cancel();
                                        }
                                    });
                            // 다이얼로그 생성
                            AlertDialog alertDialog = alertDialogBuilder.create();
                            alertDialog.setCanceledOnTouchOutside(true);
                            // 다이얼로그 보여주기
                            alertDialog.show();
                        }
                        break;
                }
            }
        };

        member_pic = (FaceOverlayView)layout.findViewById(R.id.member_pic);
        member_modify_button = (Button)layout.findViewById(R.id.member_modify_button);
        member_modify_button.setOnClickListener(btnClickListener);
        member_pic_camera = (Button)layout.findViewById(R.id.member_pic_camera);
        member_pic_camera.setOnClickListener(btnClickListener);
        member_pic_gallery = (Button)layout.findViewById(R.id.member_pic_gallery);
        member_pic_gallery.setOnClickListener(btnClickListener);
        member_pic_del = (Button)layout.findViewById(R.id.member_pic_del);
        member_pic_del.setOnClickListener(btnClickListener);
        member_pic_del.setVisibility(View.GONE);
        member_pic2 = (ImageView)layout.findViewById(R.id.member_pic2);

        return layout;
    }   // onCreateView 끝

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sp = getActivity().getSharedPreferences("maintain", MODE_PRIVATE);
        File imgFile = new  File(sp.getString("picPath", ""));
        if(imgFile.exists()){
            Uri u = Uri.fromFile(imgFile);
            Glide.with(this).load(u).into(member_pic2);
            member_pic_del.setVisibility(View.VISIBLE);
//            Glide.with(this).load(u).apply(RequestOptions.circleCropTransform()).into(member_pic2);
//            Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
//            member_pic2.setImageBitmap(myBitmap);
        } else {
            BitmapDrawable drawable = (BitmapDrawable)getResources().getDrawable(R.drawable.baseprofileimg);
            Bitmap bitmap = drawable.getBitmap();
            member_pic2.setImageBitmap(bitmap);
        }
    }

    public File createImageFile() throws IOException {
        Log.i("createImageFile", "Call");
        // Create an image file name, 외장 메모리 저장
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = timeStamp + ".jpg";
        File imageFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/linkalkPic/" + imageFileName);

        if(!imageFile.exists()) {
            imageFile.getParentFile().mkdirs();
            imageFile.createNewFile();
        }

        mCurrentPhotoPath = imageFile.getAbsolutePath();

        return imageFile;
    }

    public void TakePicIntent(){
//        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
//        mUri = Uri.fromFile(new File(Environment.getExternalStorageDirectory(), "edp_image.jpg"));
//        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mUri);
//        if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
//            startActivityForResult(takePictureIntent, CAMERA_CODE);
//        }
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.e("captureCamera Error", ex.toString());
            }
            if (photoFile != null) {
                // photoURI : file://로 시작, FileProvider(Content Provider 하위)는 content://로 시작
                // 누가(7.0)이상부터는 file://로 시작되는 Uri의 값을 다른 앱과 주고 받기가 불가능하여 content://로 변경
                Uri providerURI = FileProvider.getUriForFile(getActivity().getApplicationContext(), "com.together.linkalk", photoFile);
                mUri = providerURI;
                Log.i("imageUri", mUri.toString());

                // 인텐트에 전달할 때는 FileProvier의 Return값인 content://로만!!, providerURI의 값에 카메라 데이터를 넣어 보냄
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, providerURI);

                // file provider permission denial 해결 부분, 패키지를 필요로 하는 모든 패키지에 권한 부여 해줌
                List<ResolveInfo> resInfoList = getActivity().getApplicationContext().getPackageManager().queryIntentActivities(takePictureIntent, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo resolveInfo : resInfoList) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    getActivity().getApplicationContext().grantUriPermission(packageName, mUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }

                startActivityForResult(takePictureIntent, CAMERA_CODE);
            }
        }

    }

    public void GetPicIntent(){
        Intent pickPictureIntent = new Intent(Intent.ACTION_GET_CONTENT);
        pickPictureIntent.setType("image/*");
        if (pickPictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivityForResult(pickPictureIntent, GALLERY_CODE);
        }
    }

    public void CropPic(Uri uri){
        Intent cropPictureIntent = new Intent("com.android.camera.action.CROP");
        cropPictureIntent.setDataAndType(uri, "image/*");
        cropPictureIntent.putExtra("output", uri);
        cropPictureIntent.putExtra("outputX", 640); // crop한 이미지의 x축 크기 (integer)
        cropPictureIntent.putExtra("outputY", 480); // crop한 이미지의 y축 크기 (integer)
        cropPictureIntent.putExtra("aspectX", 4); // crop 박스의 x축 비율 (integer)
        cropPictureIntent.putExtra("aspectY", 3); // crop 박스의 y축 비율 (integer)
        cropPictureIntent.putExtra("scale", true);
        cropPictureIntent.putExtra("return-data", true);
        if (cropPictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivityForResult(cropPictureIntent, PICT_CROP_CODE);
        }
    }

    public Uri createSaveCropFile(){
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = timeStamp + ".jpg";

        Uri uri;
        String url = Environment.getExternalStorageDirectory().getAbsolutePath() + "/linkalkPic/" + imageFileName + "_crop.jpg";
        uri = Uri.fromFile(new File(url));

        return uri;
    }

    public static boolean copyFile(File srcFile, File destFile) {
        boolean result = false;
        try {
            InputStream in = new FileInputStream(srcFile);
            try {
                result = copyToFile(in, destFile);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            result = false;
        }
        return result;
    }

    private static boolean copyToFile(InputStream inputStream, File destFile) {
        try {
            OutputStream out = new FileOutputStream(destFile);
            try {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) >= 0) {
                    out.write(buffer, 0, bytesRead);
                }
            } finally {
                out.close();
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private File getImageFile(Uri uri) {
        String[] projection = { MediaStore.Images.Media.DATA };
        if (uri == null) {
            uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }

        Cursor mCursor = getActivity().getContentResolver().query(uri, projection, null, null, MediaStore.Images.Media.DATE_MODIFIED + " desc");
        if(mCursor == null || mCursor.getCount() < 1) {
            return null; // no cursor or no record
        }
        int column_index = mCursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        mCursor.moveToFirst();

        String path = mCursor.getString(column_index);

        if (mCursor !=null ) {
            mCursor.close();
            mCursor = null;
        }

        return new File(path);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode==RESULT_OK){
            if(requestCode==CAMERA_CODE){
//                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
//                File file = new File(mCurrentPhotoPath);
//                Uri contentUri = Uri.fromFile(file);
//                mediaScanIntent.setData(contentUri);
//                getActivity().sendBroadcast(mediaScanIntent);
//
//                CropPic(contentUri);
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                File file = new File(mCurrentPhotoPath);
                mUri = Uri.fromFile(file);
                mediaScanIntent.setData(mUri);
                getActivity().sendBroadcast(mediaScanIntent);

                CropPic(mUri);
            } else if(requestCode==GALLERY_CODE){
                mUri = data.getData();
                File origin = getImageFile(mUri);
                mUri = createSaveCropFile();
                File copy = new File(mUri.getPath());
                copyFile(origin, copy);

                CropPic(mUri);
            } else if(requestCode==PICT_CROP_CODE){
//                Bundle extras = data.getExtras();
//                Bitmap imageBitmap = (Bitmap) extras.get("data");
//                member_pic.setBitmap(imageBitmap);
                String path = mUri.getPath();
                Bitmap imageBitmap = BitmapFactory.decodeFile(path);
                member_pic.setBitmap(imageBitmap);
            }
        }
    }

    // 회원 정보 수정을 위해 서버에 등록된 나의 정보를 받아오는 Async
    class GetMyProfile extends AsyncTask<Void, Void, String> {

        String nickname = null;
        String language = null;
        String location = null;
        String introduce = null;
        String hobby1 = null;
        String hobby2 = null;
        String hobby3 = null;
        String hobby4 = null;
        String hobby5 = null;

        @Override
        protected String doInBackground(Void... params) {
            try{
                // 서버에서 추천 친구 목록을 받기 위해 요청하는 부분
                SharedPreferences sharedPreferences = getActivity().getSharedPreferences("maintain", Context.MODE_PRIVATE);
                String sessionID = sharedPreferences.getString("sessionID", "");

                Log.i("sessionID",sessionID);
                JSONObject object = new JSONObject();
                object.put("sessionID", sessionID);
                String send = object.toString();

                URL url = new URL("http://www.o-ddang.com/linkalk/sendMyDetailProfile.php");
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
                os.write(send.getBytes());
                os.flush();

                // 서버에서 추천 친구 목록 데이터를 받아오는 부분
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
            } catch (JSONException e) {
                e.printStackTrace();
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            Log.i("newMemberList", s);
            SharedPreferences newMemberShared = getActivity().getSharedPreferences("MyProfile", Context.MODE_PRIVATE);
            SharedPreferences.Editor MyProfileEditor = newMemberShared.edit();

            try {
                JSONObject object = new JSONObject(s);

                if(!TextUtils.isEmpty(object.getString("nickname"))){
                    nickname = object.getString("nickname");
                }
                if(!TextUtils.isEmpty(object.getString("language"))){
                    language = object.getString("language");
                }
                if(!TextUtils.isEmpty(object.getString("location"))){
                    location = object.getString("location");
                }
                if(!TextUtils.isEmpty(object.getString("introduce"))){
                    introduce = object.getString("introduce");
                }
                if(!TextUtils.isEmpty(object.getString("hobby1"))){
                    hobby1 = object.getString("hobby1");
                }
                if(!TextUtils.isEmpty(object.getString("hobby2"))){
                    hobby2 = object.getString("hobby2");
                }
                if(!TextUtils.isEmpty(object.getString("hobby3"))){
                    hobby3 = object.getString("hobby3");
                }
                if(!TextUtils.isEmpty(object.getString("hobby4"))){
                    hobby4 = object.getString("hobby4");
                }
                if(!TextUtils.isEmpty(object.getString("hobby5"))){
                    hobby5 = object.getString("hobby5");
                }

                MyProfileEditor.putString("nickname", nickname);
                MyProfileEditor.putString("language", language);
                MyProfileEditor.putString("location", location);
                MyProfileEditor.putString("introduce", introduce);
                MyProfileEditor.putString("hobby1", hobby1);
                MyProfileEditor.putString("hobby2", hobby2);
                MyProfileEditor.putString("hobby3", hobby3);
                MyProfileEditor.putString("hobby4", hobby4);
                MyProfileEditor.putString("hobby5", hobby5);
                MyProfileEditor.commit();

                Intent intent = new Intent(getActivity().getApplicationContext(), MyProfileModify.class);
                startActivity(intent);

            } catch (JSONException e) {
                e.printStackTrace();
            }

        }
    }

    // 등록된 프로필 사진을 지우는 어싱크
    class DeleteProfilePic extends AsyncTask<Void, Void, Void>{

        @Override
        protected Void doInBackground(Void... params) {
            SharedPreferences sharedPreferences = getActivity().getSharedPreferences("maintain", Context.MODE_PRIVATE);
            String sessionID = sharedPreferences.getString("sessionID", "");

            try {
                URL url = new URL("http://www.o-ddang.com/linkalk/deleteImageFile.php");
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

                String signal = URLEncoder.encode("signal", "UTF-8")+"="+URLEncoder.encode("delete", "UTF-8");
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
                String line = null;
                while((line = rd.readLine()) != null){
                    Log.d("delete state", line);
                    System.out.println("delete state : " + line);
                }


            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }


            return null;
        }
    }

}
