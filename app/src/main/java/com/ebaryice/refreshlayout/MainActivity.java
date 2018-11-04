package com.ebaryice.refreshlayout;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    RefreshRecycleLayout mRefreshRecycleLayout;
    RecyclerView mRecyclerView;
    MyAdapter adapter;
    List<String> mStrings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRefreshRecycleLayout = findViewById(R.id.refreshLayout);
        mRecyclerView = findViewById(R.id.recyclerView);
        mStrings = new ArrayList<String>();
        for (int i = 0; i <= 50; i++){
            mStrings.add("" + i);
        }
        adapter = new MyAdapter(mStrings,this);
        mRecyclerView.setAdapter(adapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRefreshRecycleLayout.setOnRefreshListener(new RefreshRecycleLayout.PullToRefreshListener() {
            @Override
            public void onRefresh() {
                try{
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mRefreshRecycleLayout.finishRefreshing();
            }
        },1);
    }
}
