package com.deniscerri.ytdlnis.page;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.deniscerri.ytdlnis.MainActivity;
import com.deniscerri.ytdlnis.R;
import com.deniscerri.ytdlnis.adapter.DownloadsRecyclerViewAdapter;
import com.deniscerri.ytdlnis.database.DBManager;
import com.deniscerri.ytdlnis.database.Video;
import com.deniscerri.ytdlnis.service.DownloadInfo;
import com.deniscerri.ytdlnis.service.IDownloaderListener;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * A fragment representing a list of Items.
 */
public class DownloadsFragment extends Fragment implements DownloadsRecyclerViewAdapter.OnItemClickListener, View.OnClickListener, View.OnLongClickListener{
    private boolean downloading = false;
    private View fragmentView;
    private DBManager dbManager;
    Context context;
    Activity activity;
    MainActivity mainActivity;
    Context fragmentContext;
    private LayoutInflater layoutinflater;
    private ShimmerFrameLayout shimmerCards;
    private MaterialToolbar topAppBar;
    private RecyclerView recyclerView;
    private DownloadsRecyclerViewAdapter downloadsRecyclerViewAdapter;
    private BottomSheetDialog bottomSheet;
    private BottomSheetDialog sortSheet;
    private Handler uiHandler;
    private RelativeLayout no_results;
    private LinearLayout selectionChips;
    private ChipGroup websiteGroup;
    private ArrayList<Video> downloadsObjects;
    public ArrayList<Video> selectedObjects;
    private LinearProgressIndicator progressBar;
    private ExtendedFloatingActionButton deleteFab;
    private String format = "";
    private String website = "";
    private String sort = "DESC";
    private String searchQuery = "";

    private static final String TAG = "downloadsFragment";


    public IDownloaderListener listener = new IDownloaderListener() {

        public void onDownloadStart(DownloadInfo downloadInfo) {
            try{
                if(downloadInfo != null){
                    Video v = downloadInfo.getVideo();
                    progressBar = fragmentView.findViewWithTag(v.getURL()+v.getDownloadedType()+"##progress");
                }
            }catch(Exception ignored){}
            downloading = true;
        }

        public void onDownloadProgress(DownloadInfo info) {
            activity.runOnUiThread(() -> {
                try{
                    int progress = info.getProgress();
                    Video v = info.getVideo();
                    if (progress > 0) {
                        progressBar = fragmentView.findViewWithTag(v.getURL()+v.getDownloadedType()+"##progress");
                        progressBar.setProgressCompat(progress, true);
                    }
                }catch(Exception ignored){
                }
            });
        }

        public void onDownloadError(DownloadInfo info){
            try{
                Video item = info.getVideo();
                Log.e(TAG, item.toString());
                String url = item.getURL();
                String type = info.getDownloadType();
                Video v = findVideo(url, type);
                Log.e(TAG, v.toString());
                dbManager = new DBManager(context);
                dbManager.clearHistoryItem(v, false);
                int position = downloadsObjects.indexOf(v);
                downloadsObjects.remove(v);
                downloadsRecyclerViewAdapter.notifyItemRemoved(position);

                if (downloadsObjects.isEmpty()) initCards();
                downloading = false;
            }catch(Exception e){
                e.printStackTrace();
            }
        }

        public void onDownloadEnd(DownloadInfo downloadInfo) {
            Video item = downloadInfo.getVideo();
            String url = item.getURL();
            String type = downloadInfo.getDownloadType();
            item = findVideo(url, type);
            try{
                // MEDIA SCAN
                ArrayList<File> files = new ArrayList<>();
                String title = downloadInfo.getVideo().getTitle().replaceAll("[^a-zA-Z0-9]","");
                String pathTmp = "";
                File path = new File(downloadInfo.getDownloadPath());
                for( File file : path.listFiles() ){
                    if(file.isFile()){
                        pathTmp = file.getAbsolutePath().replaceAll("[^a-zA-Z0-9]","");
                        if (pathTmp.contains(title)){
                            files.add(file);
                        }
                    }
                }

                String[] paths = new String[files.size()];
                for (int i = 0; i < files.size(); i++) paths[i] = files.get(i).getAbsolutePath();
                MediaScannerConnection.scanFile(context, paths, null, null);
                item.setDownloadedType(type);

                Calendar cal = Calendar.getInstance();
                Date date = new Date();
                cal.setTime(date);
                int day = cal.get(Calendar.DAY_OF_MONTH);
                String month = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
                int year = cal.get(Calendar.YEAR);

                DateFormat formatter = new SimpleDateFormat("HH:mm", Locale.getDefault());
                String time = formatter.format(date);
                String downloadedTime = day + " " + month + " " + year + " " + time;

                String downloadPath = "";
                try{
                    downloadPath = paths[0];
                }catch(Exception e){
                    e.printStackTrace();
                }

                dbManager = new DBManager(context);
                try {
                    item.setDownloadedTime(downloadedTime);
                    item.setDownloadPath(downloadPath);
                    item.setQueuedDownload(false);
                    dbManager.updateHistoryItem(item);
                    dbManager.close();
                    downloadsRecyclerViewAdapter.notifyItemChanged(downloadsObjects.indexOf(item));
                } catch (Exception ignored) {}
                downloading = false;
            }catch(Exception ignored){}
        }

        @Override
        public void onDownloadCancel(DownloadInfo info) {
            Video v = info.getVideo();
            v = findVideo(v.getURL(), v.getDownloadedType());
            try {
                downloadsObjects.remove(v);
                dbManager = new DBManager(context);
                dbManager.clearHistoryItem(v,false);
                dbManager.close();
                downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);

                if (downloadsObjects.isEmpty()) initCards();
                downloading = false;
            }catch (Exception ignored){}
        }

        @Override
        public void onDownloadCancelAll(DownloadInfo downloadInfo){
            try {
                dbManager = new DBManager(context);
                dbManager.clearDownloadingHistory();
                dbManager.close();
                initCards();
                downloading = false;
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        public void onDownloadServiceEnd() {}
    };

    public void setDownloading(boolean d){
        downloading = d;
    }


    public DownloadsFragment() {
    }

    @SuppressWarnings("unused")
    public static DownloadsFragment newInstance() {
        DownloadsFragment fragment = new DownloadsFragment();
        Bundle args = new Bundle();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        fragmentView = inflater.inflate(R.layout.fragment_downloads, container, false);
        context = fragmentView.getContext().getApplicationContext();
        activity = getActivity();
        mainActivity = (MainActivity) activity;
        fragmentContext = fragmentView.getContext();
        layoutinflater = LayoutInflater.from(context);
        shimmerCards = fragmentView.findViewById(R.id.shimmer_downloads_framelayout);
        topAppBar = fragmentView.findViewById(R.id.downloads_toolbar);
        no_results = fragmentView.findViewById(R.id.downloads_no_results);
        selectionChips = fragmentView.findViewById(R.id.downloads_selection_chips);
        websiteGroup = fragmentView.findViewById(R.id.website_chip_group);
        deleteFab = fragmentView.findViewById(R.id.delete_selected_fab);

        deleteFab.setTag("deleteSelected");
        deleteFab.setOnClickListener(this);

        uiHandler = new Handler(Looper.getMainLooper());
        downloadsObjects = new ArrayList<>();
        selectedObjects = new ArrayList<>();
        downloading = mainActivity.isDownloadServiceRunning();

        recyclerView = fragmentView.findViewById(R.id.recycler_view_downloads);
        downloadsRecyclerViewAdapter = new DownloadsRecyclerViewAdapter(downloadsObjects, this, activity);
        recyclerView.setAdapter(downloadsRecyclerViewAdapter);
        recyclerView.setNestedScrollingEnabled(false);

        initMenu();
        initChips();
        initCards();
        return fragmentView;
    }

    public void initCards(){
        shimmerCards.startShimmer();
        shimmerCards.setVisibility(View.VISIBLE);
        downloadsRecyclerViewAdapter.clear();
        no_results.setVisibility(View.GONE);
        selectionChips.setVisibility(View.VISIBLE);

        dbManager = new DBManager(context);
        try{
            Thread thread = new Thread(() -> {
                if (!downloading) dbManager.clearDownloadingHistory();
                downloadsObjects = dbManager.getHistory("", format,website,sort);
                uiHandler.post(() -> {
                    downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);
                    shimmerCards.stopShimmer();
                    shimmerCards.setVisibility(View.GONE);
                    updateWebsiteChips();
                });

                if(downloadsObjects.size() == 0){
                    uiHandler.post(() -> {
                        no_results.setVisibility(View.VISIBLE);
                        selectionChips.setVisibility(View.GONE);
                        websiteGroup.removeAllViews();
                    });
                }
                dbManager.close();
            });
            thread.start();
        }catch(Exception e){
            Log.e(TAG, e.toString());
        }

    }

    public void scrollToTop(){
        recyclerView.scrollToPosition(0);
        new Handler(Looper.getMainLooper()).post(() -> ((AppBarLayout) topAppBar.getParent()).setExpanded(true, true));
    }

    private void initMenu(){
        MenuItem.OnActionExpandListener onActionExpandListener = new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                return true;
            }
        };

        MaterialToolbar toolbar = fragmentView.findViewById(R.id.downloads_toolbar);

        topAppBar.getMenu().findItem(R.id.search_downloads).setOnActionExpandListener(onActionExpandListener);
        SearchView searchView = (SearchView) topAppBar.getMenu().findItem(R.id.search_downloads).getActionView();
        searchView.setInputType(InputType.TYPE_CLASS_TEXT);
        searchView.setQueryHint(getString(R.string.search_history_hint));

        dbManager = new DBManager(context);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchQuery = query;
                topAppBar.getMenu().findItem(R.id.search_downloads).collapseActionView();
                downloadsObjects = dbManager.getHistory(query, format,website,sort);
                downloadsRecyclerViewAdapter.clear();
                downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);

                if(downloadsObjects.size() == 0) {
                    no_results.setVisibility(View.VISIBLE);
                    selectionChips.setVisibility(View.GONE);
                    websiteGroup.removeAllViews();
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchQuery = newText;
                downloadsObjects = dbManager.getHistory(newText, format,website,sort);
                downloadsRecyclerViewAdapter.clear();
                downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);

                if (downloadsObjects.size() == 0) {
                    no_results.setVisibility(View.VISIBLE);
                    selectionChips.setVisibility(View.GONE);
                }else{
                    no_results.setVisibility(View.GONE);
                    selectionChips.setVisibility(View.VISIBLE);
                }

                return true;
            }
        });

        topAppBar.setOnClickListener(view -> scrollToTop());

        topAppBar.setOnMenuItemClickListener((MenuItem m) -> {
            int itemID = m.getItemId();
            if(itemID == R.id.remove_downloads) {
                if (downloadsObjects.size() == 0) {
                    Toast.makeText(context, R.string.history_is_empty, Toast.LENGTH_SHORT).show();
                    return true;
                }

                MaterialAlertDialogBuilder delete_dialog = new MaterialAlertDialogBuilder(fragmentContext);
                delete_dialog.setTitle(getString(R.string.confirm_delete_history));
                delete_dialog.setMessage(getString(R.string.confirm_delete_history_desc));
                delete_dialog.setNegativeButton(getString(R.string.cancel), (dialogInterface, i) -> {
                    dialogInterface.cancel();
                });
                delete_dialog.setPositiveButton(getString(R.string.ok), (dialogInterface, i) -> {
                    dbManager.clearHistory();
                    downloadsRecyclerViewAdapter.clear();
                    downloadsObjects.clear();
                    downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);
                    no_results.setVisibility(View.VISIBLE);
                    selectionChips.setVisibility(View.GONE);
                    websiteGroup.removeAllViews();
                });
                delete_dialog.show();
            }else if(itemID == R.id.remove_deleted_downloads){
                if (downloadsObjects.size() == 0) {
                    Toast.makeText(context, R.string.history_is_empty, Toast.LENGTH_SHORT).show();
                    return true;
                }

                MaterialAlertDialogBuilder delete_dialog = new MaterialAlertDialogBuilder(fragmentContext);
                delete_dialog.setTitle(getString(R.string.confirm_delete_history));
                delete_dialog.setMessage(getString(R.string.confirm_delete_history_deleted_desc));
                delete_dialog.setNegativeButton(getString(R.string.cancel), (dialogInterface, i) -> {
                    dialogInterface.cancel();
                });
                delete_dialog.setPositiveButton(getString(R.string.ok), (dialogInterface, i) -> {
                    dbManager.clearDeletedHistory();
                    initCards();
                });
                delete_dialog.show();
            }else if (itemID == R.id.remove_duplicates){
                if (downloadsObjects.size() == 0) {
                    Toast.makeText(context, R.string.history_is_empty, Toast.LENGTH_SHORT).show();
                    return true;
                }

                MaterialAlertDialogBuilder delete_dialog = new MaterialAlertDialogBuilder(fragmentContext);
                delete_dialog.setTitle(getString(R.string.confirm_delete_history));
                delete_dialog.setMessage(getString(R.string.confirm_delete_history_duplicates_desc));
                delete_dialog.setNegativeButton(getString(R.string.cancel), (dialogInterface, i) -> {
                    dialogInterface.cancel();
                });
                delete_dialog.setPositiveButton(getString(R.string.ok), (dialogInterface, i) -> {
                    dbManager.clearDuplicateHistory();
                    initCards();
                });
                delete_dialog.show();
            }else if (itemID == R.id.remove_downloading){
                if (downloadsObjects.size() == 0) {
                    Toast.makeText(context, R.string.history_is_empty, Toast.LENGTH_SHORT).show();
                    return true;
                }

                MaterialAlertDialogBuilder delete_dialog = new MaterialAlertDialogBuilder(fragmentContext);
                delete_dialog.setTitle(getString(R.string.confirm_delete_history));
                delete_dialog.setMessage(getString(R.string.confirm_delete_downloading_desc));
                delete_dialog.setNegativeButton(getString(R.string.cancel), (dialogInterface, i) -> {
                    dialogInterface.cancel();
                });
                delete_dialog.setPositiveButton(getString(R.string.ok), (dialogInterface, i) -> {
                    dbManager.clearDownloadingHistory();
                    mainActivity.cancelDownloadService();
                    initCards();
                });
                delete_dialog.show();
            }
            return true;
        });

    }

    private void initChips(){
        //sort and history/downloading switch
        Chip sortChip = fragmentView.findViewById(R.id.sort_chip);
        sortChip.setOnClickListener(view -> {
            sortSheet = new BottomSheetDialog(fragmentContext);
            sortSheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
            sortSheet.setContentView(R.layout.downloads_sort_sheet);

            TextView newest = sortSheet.findViewById(R.id.newest);
            TextView oldest = sortSheet.findViewById(R.id.oldest);

            newest.setOnClickListener(view1 -> {
                sort = "DESC";
                downloadsObjects = dbManager.getHistory(searchQuery, format,website,sort);
                downloadsRecyclerViewAdapter.clear();
                downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);
                sortSheet.cancel();
            });

            oldest.setOnClickListener(view1 -> {
                sort = "ASC";
                downloadsObjects = dbManager.getHistory(searchQuery, format,website,sort);
                downloadsRecyclerViewAdapter.clear();
                downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);
                sortSheet.cancel();
            });

            TextView cancel = sortSheet.findViewById(R.id.cancel);
            cancel.setOnClickListener(view1 -> {
                sortSheet.cancel();
            });

            sortSheet.show();
            sortSheet.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        });

        //format
        Chip audio = fragmentView.findViewById(R.id.audio_chip);
        audio.setOnClickListener(view -> {
            if (audio.isChecked()) {
                format = "audio";
                if (recyclerView.getVisibility() == View.GONE){

                }
                downloadsObjects = dbManager.getHistory(searchQuery,format,website,sort);
                audio.setChecked(true);
            }else {
                format = "";
                downloadsObjects = dbManager.getHistory(searchQuery,format,website,sort);
                audio.setChecked(false);
            }

            downloadsRecyclerViewAdapter.clear();
            downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);
        });

        Chip video = fragmentView.findViewById(R.id.video_chip);
        video.setOnClickListener(view -> {
            if (video.isChecked()) {
                format = "video";
                downloadsObjects = dbManager.getHistory(searchQuery,format,website,sort);
                video.setChecked(true);
            }else {
                format = "";
                downloadsObjects = dbManager.getHistory(searchQuery,format,website,sort);
                video.setChecked(false);
            }

            downloadsRecyclerViewAdapter.clear();
            downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);
        });

    }



    private void updateWebsiteChips(){
        websiteGroup.removeAllViews();

        ArrayList<String> websites = downloadsRecyclerViewAdapter.getWebsites();
        for (int i = 0; i < websites.size(); i++){
            String w = websites.get(i);
            Chip tmp = (Chip) layoutinflater.inflate(R.layout.filter_chip, websiteGroup, false);
            tmp.setText(w);
            tmp.setId(i);
            tmp.setOnClickListener(view -> {
                if (tmp.isChecked()){
                    website = (String) tmp.getText();
                    downloadsObjects = dbManager.getHistory(searchQuery,format,website,sort);
                    websiteGroup.check(view.getId());
                }else{
                    website = "";
                    downloadsObjects = dbManager.getHistory(searchQuery,format,website,sort);
                    websiteGroup.clearCheck();
                }
                downloadsRecyclerViewAdapter.clear();
                downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);
            });
            websiteGroup.addView(tmp);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if(id == R.id.bottomsheet_remove_button){
            removedownloadsItem((Integer) v.getTag());
        }else if(id == R.id.bottom_sheet_link){
            openLinkIntent((Integer) v.getTag());
        }else if(id == R.id.bottomsheet_open_file_button){
            openFileIntent((Integer) v.getTag());
        }else if (id == R.id.delete_selected_fab){
            removeSelectedItems();
        }
    }

    @Override
    public boolean onLongClick(View v) {
        int id = v.getId();
        if(id == R.id.bottom_sheet_link){
            copyLinkToClipBoard((Integer) v.getTag());
            return true;
        }
        return false;
    }

    private void removeSelectedItems(){
        if(bottomSheet != null) bottomSheet.hide();
        final boolean[] delete_file = {false};
        dbManager = new DBManager(context);

        MaterialAlertDialogBuilder delete_dialog = new MaterialAlertDialogBuilder(fragmentContext);
        delete_dialog.setTitle(getString(R.string.you_are_going_to_delete_multiple_items));
        delete_dialog.setMultiChoiceItems(new String[]{getString(R.string.delete_files_too)}, new boolean[]{false}, (dialogInterface, i, b) -> delete_file[0] = b);
        delete_dialog.setNegativeButton(getString(R.string.cancel), (dialogInterface, i) -> {
            dialogInterface.cancel();
        });
        delete_dialog.setPositiveButton(getString(R.string.ok), (dialogInterface, i) -> {
            for (int j = 0; j < selectedObjects.size(); j++){
                Video v = selectedObjects.get(j);
                int position = downloadsObjects.indexOf(v);
                downloadsObjects.remove(v);
                downloadsRecyclerViewAdapter.notifyItemRemoved(position);
                downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);
                dbManager.clearHistoryItem(v, delete_file[0]);
            }
            updateWebsiteChips();
            dbManager.close();
            selectedObjects = new ArrayList<>();
            downloadsRecyclerViewAdapter.clearCheckedVideos();
            deleteFab.setVisibility(View.GONE);

            if(downloadsObjects.size() == 0){
                uiHandler.post(() -> {
                    no_results.setVisibility(View.VISIBLE);
                    selectionChips.setVisibility(View.GONE);
                    websiteGroup.removeAllViews();
                });
            }
        });
        delete_dialog.show();
    }

    private void removedownloadsItem(int position){
        if(bottomSheet != null) bottomSheet.hide();
        final boolean[] delete_file = {false};
        dbManager = new DBManager(context);

        Video v = downloadsObjects.get(position);
        MaterialAlertDialogBuilder delete_dialog = new MaterialAlertDialogBuilder(fragmentContext);
        delete_dialog.setTitle(getString(R.string.you_are_going_to_delete) + " \""+v.getTitle()+"\"!");
        delete_dialog.setMultiChoiceItems(new String[]{getString(R.string.delete_file_too)}, new boolean[]{false}, (dialogInterface, i, b) -> delete_file[0] = b);
        delete_dialog.setNegativeButton(getString(R.string.cancel), (dialogInterface, i) -> {
            dialogInterface.cancel();
        });
        delete_dialog.setPositiveButton(getString(R.string.ok), (dialogInterface, i) -> {
            downloadsObjects.remove(position);
            downloadsRecyclerViewAdapter.notifyItemRemoved(position);
            downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);
            updateWebsiteChips();
            dbManager.clearHistoryItem(v, delete_file[0]);
            dbManager.close();

            if(downloadsObjects.size() == 0){
                uiHandler.post(() -> {
                    no_results.setVisibility(View.VISIBLE);
                    selectionChips.setVisibility(View.GONE);
                    websiteGroup.removeAllViews();
                });
            }
        });
        delete_dialog.show();
    }

    private void copyLinkToClipBoard(int position){
        String url = downloadsObjects.get(position).getURL();
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(getString(R.string.url), url);
        clipboard.setPrimaryClip(clip);
        if(bottomSheet != null) bottomSheet.hide();
        Toast.makeText(context, getString(R.string.link_copied_to_clipboard), Toast.LENGTH_SHORT).show();
    }

    private void openLinkIntent(int position){
        String url =downloadsObjects.get(position).getURL();
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        if(bottomSheet != null) bottomSheet.hide();
        startActivity(i);
    }

    private void openFileIntent(int position){
        String downloadPath =downloadsObjects.get(position).getDownloadPath();
        File file = new File(downloadPath);

        Uri uri = FileProvider.getUriForFile(fragmentContext, fragmentContext.getPackageName() + ".fileprovider", file);
        String mime = mainActivity.getContentResolver().getType(uri);

        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(i);
    }

    @Override
    public void onCardClick(int position, boolean isFilePresent) {
        bottomSheet = new BottomSheetDialog(fragmentContext);
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
        bottomSheet.setContentView(R.layout.downloads_bottom_sheet);
        Video video = downloadsObjects.get(position);

        TextView title = bottomSheet.findViewById(R.id.bottom_sheet_title);
        title.setText(video.getTitle());

        TextView author = bottomSheet.findViewById(R.id.bottom_sheet_author);
        author.setText(video.getAuthor());

        Button link = bottomSheet.findViewById(R.id.bottom_sheet_link);
        String url = video.getURL();
        link.setText(url);
        link.setTag(position);
        link.setOnClickListener(this);
        link.setOnLongClickListener(this);

        Button remove = bottomSheet.findViewById(R.id.bottomsheet_remove_button);
        remove.setTag(position);
        remove.setOnClickListener(this);

        Button openFile = bottomSheet.findViewById(R.id.bottomsheet_open_file_button);
        openFile.setTag(position);
        openFile.setOnClickListener(this);
        if (!isFilePresent) openFile.setVisibility(View.GONE);

        bottomSheet.show();
        bottomSheet.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override
    public void onCardSelect(int position, boolean add) {
        Video video = downloadsObjects.get(position);
        if (add) selectedObjects.add(video); else selectedObjects.remove(video);
        if(selectedObjects.size() > 1){
            deleteFab.setVisibility(View.VISIBLE);
        } else {
            deleteFab.setVisibility(View.GONE);
        }
    }

    @Override
    public void onButtonClick(int position) {
        Video vid = downloadsObjects.get(position);
        try {
            mainActivity.removeItemFromDownloadQueue(vid, vid.getDownloadedType());
        }catch (Exception e){
            DownloadInfo info = new DownloadInfo();
            info.setVideo(vid);
            info.setDownloadType(vid.getDownloadedType());
            listener.onDownloadCancel(info);
        }
    }

    public Video findVideo(String url, String type) {
        for (int i = 0; i < downloadsObjects.size(); i++) {
            Video v = downloadsObjects.get(i);
            if ((v.getURL()).equals(url) && v.getDownloadedType().equals(type) && v.isQueuedDownload()) {
                return v;
            }
        }

        return null;
    }

}