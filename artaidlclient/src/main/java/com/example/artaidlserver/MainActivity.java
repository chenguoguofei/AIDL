package com.example.artaidlserver;//package com.example.artaidlclient;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;


import com.example.artaidlclient.R;

import java.util.List;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BookManagerActivity";
    private static final int MESSAGE_NEW_BOOK_ARRIVED = 1;

    private IBookManager mRemoteBookManager;

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MESSAGE_NEW_BOOK_ARRIVED) {
                Log.d(TAG, "receive new book :" + msg.obj);
            } else {
                super.handleMessage(msg);
            }
        }
    };

    private final IBinder.DeathRecipient mDeathRecipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            Log.d(TAG, "binder died. tname:" + Thread.currentThread().getName());
            if (mRemoteBookManager == null)
                return;
            mRemoteBookManager.asBinder().unlinkToDeath(mDeathRecipient, 0);
            mRemoteBookManager = null;
            // TODO:这里重新绑定远程Service
        }
    };

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            new Thread(() -> {
                IBookManager bookManager = IBookManager.Stub.asInterface(service);
                mRemoteBookManager = bookManager;
                try {
                    mRemoteBookManager.asBinder().linkToDeath(mDeathRecipient, 0);
                    List<Book> list = bookManager.getBookList();
                    Log.i(TAG, "query book list, list type:"
                            + list.getClass().getCanonicalName());
                    Log.i(TAG, "query book list:" + list);
                    Book newBook = new Book(3, "Android进阶");
                    bookManager.addBook(newBook);
                    Log.i(TAG, "add book:" + newBook);
                    List<Book> newList = bookManager.getBookList();
                    Log.i(TAG, "query book list:" + newList.toString());
                    bookManager.registerListener(mOnNewBookArrivedListener);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }).start();
        }

        public void onServiceDisconnected(ComponentName className) {
            mRemoteBookManager = null;
            Log.d(TAG, "onServiceDisconnected. tname:" + Thread.currentThread().getName());
        }
    };

    private final IOnNewBookArrivedListener mOnNewBookArrivedListener = new IOnNewBookArrivedListener.Stub() {

        @Override
        public void onNewBookArrived(Book newBook) throws RemoteException {
            mHandler.obtainMessage(MESSAGE_NEW_BOOK_ARRIVED, newBook)
                    .sendToTarget();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i(TAG,"client begin bind");
        Intent intent = new Intent();
        intent.setAction("intent.action.aidl.service");
        intent.setComponent(new ComponentName("com.example.artaidlserver","com.example.artaidlserver.BookManagerService"));
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        Log.i(TAG,"end begin bind");
    }

    public void onButton1Click(View view) {
        Toast.makeText(this, "click button1", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            if (mRemoteBookManager != null) {
                try {
                    List<Book> newList = mRemoteBookManager.getBookList();
                    Log.i(TAG,"newList:"+newList);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        if (mRemoteBookManager != null
                && mRemoteBookManager.asBinder().isBinderAlive()) {
            try {
                Log.i(TAG, "unregister listener:" + mOnNewBookArrivedListener);
                mRemoteBookManager
                        .unregisterListener(mOnNewBookArrivedListener);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        unbindService(mConnection);
        super.onDestroy();
    }
}