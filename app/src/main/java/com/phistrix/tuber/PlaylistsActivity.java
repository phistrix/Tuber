package com.phistrix.tuber;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistListResponse;
import com.google.api.services.youtube.model.PlaylistSnippet;
import com.google.api.services.youtube.model.PlaylistStatus;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by phist on 1/5/2017.
 */

public class PlaylistsActivity extends AppCompatActivity {
    private static final int REQUEST_AUTHORIZATION = 0;
    public static final String PLAYLIST_KEY = "playlist";

    final HttpTransport mTransport = AndroidHttp.newCompatibleTransport();
    final JsonFactory mJsonFactory = new GsonFactory();

    GoogleAccountCredential mCredential;
    YouTube mYoutube;

    ListView mPlaylistListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlists);

        mPlaylistListView = (ListView)findViewById(R.id.listViewPlaylists);

        String chosenAccountName = getIntent().getStringExtra(MainActivity.ACCOUNT_KEY);

        mCredential =
                GoogleAccountCredential.usingOAuth2(getApplicationContext(), Lists.newArrayList(Auth.SCOPES));
        mCredential.setSelectedAccountName(chosenAccountName);
        mCredential.setBackOff(new ExponentialBackOff());

        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.GET_ACCOUNTS);

        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            loadPlaylists();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.GET_ACCOUNTS},
                    0);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 0: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    loadPlaylists();

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
        }
    }


    private void loadPlaylists() {
        AsyncTask<Void, Void, List<Playlist>> task = new AsyncTask<Void, Void, List<Playlist>>() {
            @Override
            protected List<Playlist> doInBackground(Void... params) {
                List<Playlist> playlists = new ArrayList<Playlist>();
                List<String> scopes = Lists.newArrayList("https://www.googleapis.com/auth/youtube");
                String appName = getResources().getString(R.string.app_name);
                mYoutube =
                        new YouTube.Builder(mTransport, mJsonFactory, mCredential).setApplicationName(
                                appName).build();

                try {
                    // Authorize the request.

                    PlaylistListResponse playlistListResponse = mYoutube.playlists().
                            list("snippet").setMine(true).set("hl", "en").execute();

                    playlists = playlistListResponse.getItems();
                    int i = 0;

                } catch (UserRecoverableAuthIOException e) {
                    int i = 0;
                    //UserRecoverableAuthIOException exception = (UserRecoverableAuthIOException) e.getCause();
                    startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
                } catch (GoogleAuthIOException e) {
                    // This probably means you need to recreate the keystore at https://console.developers.google.com
                } catch (java.io.IOException e) {
                    int i = 0;
                    //ignore
                }

                return playlists;
            }

            @Override
            protected void onPostExecute(List<Playlist> playlists) {
                final ArrayList<TuberPlaylist> tuberPlaylists = new ArrayList<>(playlists.size());
                for (Playlist playlist : playlists) {
                    TuberPlaylist tuberPlaylist = new TuberPlaylist(playlist);
                    tuberPlaylists.add(tuberPlaylist);
                }

                ArrayAdapter<TuberPlaylist> adapter = new ArrayAdapter<TuberPlaylist>(
                        getApplicationContext(),android.R.layout.simple_list_item_1, tuberPlaylists);
                mPlaylistListView.setAdapter(adapter);

                mPlaylistListView.setOnItemClickListener(new AdapterView.OnItemClickListener()
                {
                    // argument position gives the index of item which is clicked
                    public void onItemClick(AdapterView<?> arg0, View v, int position, long arg3)
                    {
                        TuberPlaylist selectedPlaylist=tuberPlaylists.get(position);
                        setResult(RESULT_OK, createResultIntent(selectedPlaylist));
                        finish();
                    }
                });
            }

        };
        task.execute();
    }

    private Intent createResultIntent(TuberPlaylist resultPlaylist) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(PLAYLIST_KEY, resultPlaylist);
        return resultIntent;
    }

    public void onPlaylistButtonClicked(View v){
        if(v.getId() == R.id.create_playlist_button){

            EditText playlistNameEditText = (EditText)findViewById(R.id.create_playlist_name);
            String playlistName = playlistNameEditText.getText().toString();

            if (playlistName.isEmpty()) {
                Toast.makeText(this, R.string.enter_playlist_name,
                        Toast.LENGTH_LONG).show();
            } else {
                createPlaylist(playlistName);
            }
        }
    }

    private void createPlaylist(final String playlistName) {
        AsyncTask<Void, Void, TuberPlaylist> task = new AsyncTask<Void, Void, TuberPlaylist>() {
            @Override
            protected TuberPlaylist doInBackground(Void... params) {
                try {
                    return insertPlaylist(playlistName);
                } catch (IOException exception) {
                    // ignore
                    return null;
                }
            }

            @Override
            protected void onPostExecute(TuberPlaylist playlist) {
                setResult(RESULT_OK, createResultIntent(playlist));
                finish();
            }
        };
        task.execute();
    }

    /**
     * Create a playlist and add it to the authorized account.
     */
    private TuberPlaylist insertPlaylist(String playlistName) throws IOException {

        // This code constructs the playlist resource that is being inserted.
        // It defines the playlist's title, description, and privacy status.
        PlaylistSnippet playlistSnippet = new PlaylistSnippet();
        playlistSnippet.setTitle(playlistName);
        playlistSnippet.setDescription(getResources().getString(R.string.playlist_description));
        PlaylistStatus playlistStatus = new PlaylistStatus();
        playlistStatus.setPrivacyStatus("private");

        Playlist youTubePlaylist = new Playlist();
        youTubePlaylist.setSnippet(playlistSnippet);
        youTubePlaylist.setStatus(playlistStatus);

        // Call the API to insert the new playlist. In the API call, the first
        // argument identifies the resource parts that the API response should
        // contain, and the second argument is the playlist being inserted.
        YouTube.Playlists.Insert playlistInsertCommand =
                mYoutube.playlists().insert("snippet,status", youTubePlaylist);
        Playlist playlistInserted = playlistInsertCommand.execute();
        TuberPlaylist tuberPlaylist = new TuberPlaylist(playlistInserted);

        return tuberPlaylist;

    }
}
