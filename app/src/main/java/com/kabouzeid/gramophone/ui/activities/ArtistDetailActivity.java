package com.kabouzeid.gramophone.ui.activities;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.transition.Transition;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.ksoichiro.android.observablescrollview.ObservableListView;
import com.kabouzeid.gramophone.R;
import com.kabouzeid.gramophone.adapter.AlbumAdapter;
import com.kabouzeid.gramophone.adapter.ArtistAlbumAdapter;
import com.kabouzeid.gramophone.adapter.songadapter.ArtistSongAdapter;
import com.kabouzeid.gramophone.helper.MusicPlayerRemote;
import com.kabouzeid.gramophone.lastfm.artist.LastFMArtistImageUrlLoader;
import com.kabouzeid.gramophone.loader.ArtistAlbumLoader;
import com.kabouzeid.gramophone.loader.ArtistLoader;
import com.kabouzeid.gramophone.loader.ArtistSongLoader;
import com.kabouzeid.gramophone.misc.AppKeys;
import com.kabouzeid.gramophone.misc.SmallObservableScrollViewCallbacks;
import com.kabouzeid.gramophone.model.Album;
import com.kabouzeid.gramophone.model.Artist;
import com.kabouzeid.gramophone.model.Song;
import com.kabouzeid.gramophone.ui.activities.base.AbsFabActivity;
import com.kabouzeid.gramophone.util.NavigationUtil;
import com.kabouzeid.gramophone.util.Util;
import com.kabouzeid.gramophone.util.ViewUtil;
import com.nineoldandroids.view.ViewHelper;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.util.List;

/*
*
* A lot of hackery is done in this activity. Changing things may will brake the whole activity.
*
* Should be kinda stable ONLY AS IT IS!!!
*
* */

public class ArtistDetailActivity extends AbsFabActivity {
    public static final String TAG = ArtistDetailActivity.class.getSimpleName();

    public static final String ARG_ARTIST_ID = "com.kabouzeid.gramophone.artist.id";
    public static final String ARG_ARTIST_NAME = "com.kabouzeid.gramophone.artist.name";

    private Artist artist;

    private ObservableListView songListView;
    private View statusBar;
    private ImageView artistIv;
    private View songsBackgroundView;
    private TextView artistNameTv;
    private Toolbar toolbar;
    private int toolbarHeight;
    private int headerOffset;
    private int titleViewHeight;
    private int artistImageViewHeight;
    private int toolbarColor;

    private View songListHeader;
    private RecyclerView albumRecyclerView;

    private SmallObservableScrollViewCallbacks observableScrollViewCallbacks = new SmallObservableScrollViewCallbacks() {
        @Override
        public void onScrollChanged(int scrollY, boolean b, boolean b2) {
            scrollY += artistImageViewHeight + titleViewHeight;
            super.onScrollChanged(scrollY, b, b2);
            float flexibleRange = artistImageViewHeight - headerOffset;

            // Translate album cover
            ViewHelper.setTranslationY(artistIv, Math.max(-artistImageViewHeight, -scrollY / 2));

            // Translate list background
            ViewHelper.setTranslationY(songsBackgroundView, Math.max(0, -scrollY + artistImageViewHeight));

            // Change alpha of overlay
            float alpha = Math.max(0, Math.min(1, (float) scrollY / flexibleRange));
            ViewUtil.setBackgroundAlpha(toolbar, alpha, toolbarColor);
            ViewUtil.setBackgroundAlpha(statusBar, alpha, toolbarColor);

            // Translate name text
            int maxTitleTranslationY = artistImageViewHeight;
            int titleTranslationY = maxTitleTranslationY - scrollY;
            titleTranslationY = Math.max(headerOffset, titleTranslationY);

            ViewHelper.setTranslationY(artistNameTv, titleTranslationY);

            // Translate FAB
            int fabTranslationY = titleTranslationY + titleViewHeight - (getFab().getHeight() / 2);
            ViewHelper.setTranslationY(getFab(), fabTranslationY);
        }
    };


    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setUpTranslucence(true, true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artist_detail);

        if (Util.hasLollipopSDK()) postponeEnterTransition();

        getIntentExtras();
        initViews();
        setUpObservableListViewParams();
        setUpToolBar();
        setUpViews();

        if (Util.hasLollipopSDK()) fixLollipopTransitionImageWrongSize();
        if (Util.hasLollipopSDK()) startPostponedEnterTransition();
    }

    private void initViews() {
        artistIv = (ImageView) findViewById(R.id.artist_image);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        songListView = (ObservableListView) findViewById(R.id.list);
        artistNameTv = (TextView) findViewById(R.id.artist_name);
        songsBackgroundView = findViewById(R.id.list_background);
        statusBar = findViewById(R.id.statusBar);
        songListHeader = LayoutInflater.from(this).inflate(R.layout.artist_detail_header, songListView, false);
    }

    private void setUpObservableListViewParams() {
        artistImageViewHeight = getResources().getDimensionPixelSize(R.dimen.header_image_height);
        toolbarColor = getResources().getColor(R.color.materialmusic_default_bar_color);
        toolbarHeight = Util.getActionBarSize(this);
        titleViewHeight = getResources().getDimensionPixelSize(R.dimen.title_view_height);
        headerOffset = toolbarHeight;
        headerOffset += getResources().getDimensionPixelSize(R.dimen.statusMargin);
    }

    @Override
    public String getTag() {
        return TAG;
    }

    private void setUpViews() {
        artistNameTv.setText(artist.name);

        ViewUtil.addOnGlobalLayoutListener(artistIv, new Runnable() {
            @Override
            public void run() {
                setUpArtistImageAndApplyPalette(false);
            }
        });
        setUpSongListView();
        setUpAlbumRecyclerView();
    }

    private void setUpSongListView() {
        songListView.setScrollViewCallbacks(observableScrollViewCallbacks);

        setListViewPadding();

        songListView.addHeaderView(songListHeader);

        final List<Song> songs = ArtistSongLoader.getArtistSongList(this, artist.id);
        ArtistSongAdapter songAdapter = new ArtistSongAdapter(this, songs);
        songListView.setAdapter(songAdapter);

        final View contentView = getWindow().getDecorView().findViewById(android.R.id.content);
        contentView.post(new Runnable() {
            @Override
            public void run() {
                songsBackgroundView.getLayoutParams().height = contentView.getHeight();
                observableScrollViewCallbacks.onScrollChanged(-(artistImageViewHeight + titleViewHeight), false, false);
            }
        });

        songListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // header view has position 0
                if (position == 0) {
                    return;
                }
                MusicPlayerRemote.openQueue(songs, position - 1, true);
            }
        });
    }

    private void setUpAlbumRecyclerView(){
        albumRecyclerView = (RecyclerView) songListHeader.findViewById(R.id.recycler_view);
        albumRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        List<Album> albums = ArtistAlbumLoader.getArtistAlbumList(this, artist.id);
        ArtistAlbumAdapter albumAdapter = new ArtistAlbumAdapter(this, albums);
        albumRecyclerView.setAdapter(albumAdapter);
    }

    private void setListViewPadding() {
        if (Util.isInPortraitMode(this) || Util.isTablet(this)) {
            songListView.setPadding(0, artistImageViewHeight + titleViewHeight, 0, Util.getNavigationBarHeight(this));
        } else {
            songListView.setPadding(0, artistImageViewHeight + titleViewHeight, 0, 0);
        }
    }

    private void setUpArtistImageAndApplyPalette(final boolean forceDownload) {
        LastFMArtistImageUrlLoader.loadArtistImageUrl(this, artist.name, forceDownload, new LastFMArtistImageUrlLoader.ArtistImageUrlLoaderCallback() {
            @Override
            public void onArtistImageUrlLoaded(String url) {
                Picasso.with(ArtistDetailActivity.this)
                        .load(url)
                        .placeholder(R.drawable.default_artist_image)
                        .into(artistIv, new Callback.EmptyCallback() {
                            @Override
                            public void onSuccess() {
                                super.onSuccess();
                                final Bitmap bitmap = ((BitmapDrawable) artistIv.getDrawable()).getBitmap();
                                if (bitmap != null) applyPalette(bitmap);

                            }
                        });
            }
        });
    }

    private void applyPalette(Bitmap bitmap) {
        Palette.generateAsync(bitmap, new Palette.PaletteAsyncListener() {
            @Override
            public void onGenerated(Palette palette) {
                Palette.Swatch swatch = palette.getVibrantSwatch();
                if (swatch != null) {
                    toolbarColor = swatch.getRgb();
                    artistNameTv.setBackgroundColor(swatch.getRgb());
                    artistNameTv.setTextColor(swatch.getTitleTextColor());
                } else {
                    setStandardColors();
                }
            }
        });
    }

    private void setStandardColors() {
        int titleTextColor = Util.resolveColor(this, R.attr.title_text_color);
        int defaultBarColor = getResources().getColor(R.color.materialmusic_default_bar_color);

        toolbarColor = defaultBarColor;
        artistNameTv.setBackgroundColor(defaultBarColor);
        artistNameTv.setTextColor(titleTextColor);
    }

    private void setUpToolBar() {
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(null);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void getIntentExtras() {
        Bundle intentExtras = getIntent().getExtras();
        final int artistId = intentExtras.getInt(AppKeys.E_ARTIST);
        artist = ArtistLoader.getArtist(this, artistId);
        if (artist == null) {
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_artist_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                super.onBackPressed();
                return true;
            case R.id.action_re_download_artist_image:
                Toast.makeText(ArtistDetailActivity.this, getResources().getString(R.string.updating), Toast.LENGTH_SHORT).show();
                setUpArtistImageAndApplyPalette(true);
            case R.id.action_settings:
                return true;
            case R.id.action_current_playing:
                NavigationUtil.openCurrentPlayingIfPossible(this, getSharedViewsWithFab(null));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void enableViews() {
        super.enableViews();
        songListView.setEnabled(true);
        toolbar.setEnabled(true);
    }

    @Override
    public void disableViews() {
        super.disableViews();
        songListView.setEnabled(false);
        toolbar.setEnabled(false);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void fixLollipopTransitionImageWrongSize(){
        getWindow().getSharedElementEnterTransition().addListener(new Transition.TransitionListener() {
            @Override
            public void onTransitionStart(Transition transition) {

            }

            @Override
            public void onTransitionEnd(Transition transition) {
                setUpArtistImageAndApplyPalette(false);
            }

            @Override
            public void onTransitionCancel(Transition transition) {

            }

            @Override
            public void onTransitionPause(Transition transition) {

            }

            @Override
            public void onTransitionResume(Transition transition) {

            }
        });
    }
}