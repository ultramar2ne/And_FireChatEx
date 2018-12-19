package com.bluetank.fire_chat_ex.fragment;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bluetank.fire_chat_ex.R;
import com.bluetank.fire_chat_ex.chat.GroupMessageActivity;
import com.bluetank.fire_chat_ex.chat.MessageActivity;
import com.bluetank.fire_chat_ex.model.ChatModel;
import com.bluetank.fire_chat_ex.model.UserModel;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

public class ChatFragment extends Fragment {

    private SimpleDateFormat simpleDateFormat=new SimpleDateFormat("MM.dd hh:mm"); //날짜 포멧

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view=inflater.inflate(R.layout.fragment_chat,container,false);

        RecyclerView recyclerView=(RecyclerView)view.findViewById(R.id.frag_chat_recycler);  //채팅방 목록을 표현할 recyclerview
        recyclerView.setAdapter(new ChatRecyclerViewAdapter());                                 //recyclerview의 viewadapter 설정
        recyclerView.setLayoutManager(new LinearLayoutManager(inflater.getContext()));

        return view;
    }

    class ChatRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{

        private List<ChatModel> chatModels=new ArrayList<>();          //chatmodel 형식의 채팅방 정보 저장
        private List<String> keys=new ArrayList<>();                    //방에 대한 키 저장
        private String uid;
        private ArrayList<String> destinationUsers=new ArrayList<>(); //대화 할 사람들의 데이터 담김
        public ChatRecyclerViewAdapter() {
            uid=FirebaseAuth.getInstance().getCurrentUser().getUid();

            FirebaseDatabase.getInstance().getReference().child("chatrooms").orderByChild("users/"+uid).equalTo(true).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    chatModels.clear();
                    for (DataSnapshot item:dataSnapshot.getChildren()){
                        chatModels.add(item.getValue(ChatModel.class));
                        keys.add(item.getKey()); //방에대한 키를 받아옴
                    }
                    notifyDataSetChanged(); //새로고침
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            });
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            View view=LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_chat,viewGroup,false);
            
            return new CustomViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, final int i) {

            final CustomViewHolder customViewHolder=(CustomViewHolder)viewHolder;
            String destinationUid=null;

            for(String user:chatModels.get(i).users.keySet()){  //챗방에 유저들 채크

                if (chatModels.get(i).users.size()>2){
                    destinationUsers.add(null);
                }else if(!user.equals(uid)){
                    //내가 아닌 사람들 추출
                    destinationUid=user;
                    destinationUsers.add(i,destinationUid);
                }
            }

            if (chatModels.get(i).users.size()>2){ //단톡방일 경우
                FirebaseDatabase.getInstance().getReference().child("chatrooms").child(keys.get(i)).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        ChatModel chatModel=dataSnapshot.getValue(ChatModel.class);
                        Glide.with(customViewHolder.itemView.getContext())
                                .load(chatModel.profileImageUrl)
                                .apply(new RequestOptions().circleCrop())
                                .into(customViewHolder.imageView);
                        customViewHolder.textView_title.setText(chatModel.roomName);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });

            }else {     //그외 채팅방
                FirebaseDatabase.getInstance().getReference().child("user").child(destinationUid).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                        UserModel userModel=dataSnapshot.getValue(UserModel.class);
                        Glide.with(customViewHolder.itemView.getContext())
                                .load(userModel.profileImageUrl)
                                .apply(new RequestOptions().circleCrop())
                                .into(customViewHolder.imageView);
                        customViewHolder.textView_title.setText(userModel.userName);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });
            }


            Map<String,ChatModel.Comment> commentMap=new TreeMap<>(Collections.reverseOrder());  //메세지를 내림차순으로 정렬
            commentMap.putAll(chatModels.get(i).comments);

            if (commentMap.keySet().toArray().length>0) {
                String lastMessageKey = (String) commentMap.keySet().toArray()[0]; //0번째 메세지의 키값을 추출
                customViewHolder.textView_last.setText(chatModels.get(i).comments.get(lastMessageKey).message);

                //TimeStamp
                simpleDateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));            //timezone 설정
                long unixTime = (long) chatModels.get(i).comments.get(lastMessageKey).time; //unixTime으로 시간을 받아옴
                Date date = new Date(unixTime);
                customViewHolder.textView_time.setText(simpleDateFormat.format(date));
            }

            customViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent=null;
                    if (chatModels.get(i).users.size()>2){                                       //단체 채팅방일 경우
                        intent=new Intent(view.getContext(),GroupMessageActivity.class);
                        intent.putExtra("destinationRoom",keys.get(i));                  //방의 키값을 전달
                    }else {
                        if (destinationUsers.get(i)!=null){                                     //개인 채팅방일 경우
                            intent = new Intent(view.getContext(), MessageActivity.class);
                            intent.putExtra("destinationUid", destinationUsers.get(i));//유저의 uid를 전달
                        }
                    }
                    ActivityOptions activityOptions = ActivityOptions.makeCustomAnimation(view.getContext(), R.anim.frombottom, R.anim.totop);
                    startActivity(intent, activityOptions.toBundle()); //애니메이션 추가
                }
            });
        }

        @Override
        public int getItemCount() {
            return chatModels.size();
        }

        private class CustomViewHolder extends RecyclerView.ViewHolder {

            public ImageView imageView;
            public TextView textView_title,textView_last;
            public TextView textView_time;

            public CustomViewHolder(View view) {
                super(view);

                imageView=(ImageView)view.findViewById(R.id.item_chat_image);
                textView_last=(TextView)view.findViewById(R.id.item_chat_talk);
                textView_title=(TextView)view.findViewById(R.id.item_chat_title);
                textView_time=(TextView)view.findViewById(R.id.item_chat_time);
            }
        }
    }
}
