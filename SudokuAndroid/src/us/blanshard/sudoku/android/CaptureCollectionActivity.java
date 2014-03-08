package us.blanshard.sudoku.android;

import us.blanshard.sudoku.android.WorkerFragment.Independence;
import us.blanshard.sudoku.android.WorkerFragment.Priority;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Solver;
import us.blanshard.sudoku.gen.Generator;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Set;

/**
 * Captures a list of puzzles from a .sdm file.
 */
public class CaptureCollectionActivity extends ActivityBase
    implements View.OnClickListener, TextWatcher {
  private static final String TAG = "CaptureCollection";
  private TextView mNotice;
  private TextView mCollectionName;
  private TextView mDescription;
  private Button mImport;
  private String mSource;
  private String mName;
  private boolean mReadFailed;
  private final List<JsonObject> mPuzzles = Lists.newArrayList();
  private final Set<String> mCollectionNames = Sets.newHashSet();
  private final Set<String> mCollectionSources = Sets.newHashSet();

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.collect);

    mNotice = (TextView) findViewById(R.id.notice);
    mCollectionName = (TextView) findViewById(R.id.collection_name);
    mDescription = (TextView) findViewById(R.id.description);
    mImport = (Button) findViewById(R.id.import_collection);

    mCollectionName.addTextChangedListener(this);
    mImport.setOnClickListener(this);

    Uri uri = getIntent().getData();
    URL url = null;
    String name = null;
    if (uri != null) {
      mSource = uri.toString();
      try {
        url = new URL(mSource);
      } catch (MalformedURLException e) {
        Log.e(TAG, "Bad URL " + mSource, e);
      }
      if (url != null && mSource.endsWith(".sdm")) {
        name = uri.getLastPathSegment();
      }
    }
    if (name == null) {
      Log.e(TAG, "Could not understand request: " + uri);
      finish();
      return;
    }
    mCollectionName.setText(name);
    new FetchData(this, url).execute();
    updateState();
  }

  @Override protected String getHelpPage() {
    return "collection";
  }

  @Override public void onClick(View v) {
    if (!mPuzzles.isEmpty() && v == mImport) {
      new Save(this).execute();
    }
  }

  @Override public void afterTextChanged(Editable s) {
    updateState();
  }
  @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
  @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

  private void updateState() {
    boolean havePuzzles = !mPuzzles.isEmpty();
    if (havePuzzles) {
      mDescription.setText(getString(R.string.text_collection_desc, mPuzzles.size(), mSource));
    } else {
      showNotice(mReadFailed
          ? R.string.text_loading_collection_failed
          : R.string.text_loading_collection);
    }
    boolean haveCollectionInfo = !mCollectionNames.isEmpty();
    boolean okSource = haveCollectionInfo && !mCollectionSources.contains(mSource);
    if (havePuzzles && !okSource)
      showNotice(R.string.text_already_have_collection_source);
    mName = mCollectionName.getText().toString().trim();
    boolean okName = haveCollectionInfo && !mName.isEmpty()
        && !mCollectionNames.contains(mName);
    if (havePuzzles && okSource && !okName)
      showNotice(R.string.text_already_have_collection_name);
    mImport.setEnabled(havePuzzles && okName && okSource);
    if (mImport.isEnabled())
      hideNotice();
  }

  private void showNotice(int stringId) {
    mNotice.setText(stringId);
    mNotice.setVisibility(View.VISIBLE);
  }

  private void hideNotice() {
    mNotice.setVisibility(View.GONE);
  }

  private static class FetchData extends WorkerFragment.ActivityTask<CaptureCollectionActivity, Void, Void, Boolean> {
    private final Database mDb;
    private final Set<String> mCollectionNames;
    private final Set<String> mCollectionSources;
    private final URL mUrl;
    private final List<JsonObject> mPuzzles;

    FetchData(CaptureCollectionActivity activity, URL url) {
      super(activity);
      mDb = activity.mDb;
      mCollectionNames = activity.mCollectionNames;
      mCollectionSources = activity.mCollectionSources;
      mUrl = url;
      mPuzzles = activity.mPuzzles;
    }

    @Override protected Boolean doInBackground(Void... params) {
      List<Database.CollectionInfo> collInfo = mDb.getAllCollections();
      for (Database.CollectionInfo info : collInfo) {
        mCollectionNames.add(info.name);
        if (info.source != null)
          mCollectionSources.add(info.source);
      }

      BufferedReader in = null;
      try {
        in = new BufferedReader(
            new InputStreamReader(mUrl.openStream(), Charsets.US_ASCII));
        for (String s; (s = in.readLine()) != null; ) {
          Grid grid = Grid.fromString(s);  // May throw.
          Solver.Result result = Solver.solve(grid, Prefs.MAX_SOLUTIONS);
          if (result.intersection == null) {
            throw new IllegalStateException("Not a valid puzzle: " + s);
          }
          mPuzzles.add(Generator.makePuzzleProperties(result));
        }
        if (mPuzzles.isEmpty())
          throw new IllegalStateException("No puzzles in the collection");
      } catch (Exception e) {
        Log.e(TAG, "Problem reading puzzle collection " + mUrl, e);
        return false;
      } finally {
        try { in.close(); }
        catch (IOException e) {}
      }

      return true;
    }

    @Override protected void onPostExecute(CaptureCollectionActivity activity, Boolean readSucceeded) {
      // Deal with possible null.
      activity.mReadFailed = !Boolean.TRUE.equals(readSucceeded);
      activity.updateState();
    }
  }

  private static class Save extends WorkerFragment.ActivityTask<CaptureCollectionActivity, Void, Void, Long> {
    private final Database mDb;
    private final List<JsonObject> mPuzzles;
    private final String mSource;
    private final String mName;

    Save(CaptureCollectionActivity activity) {
      super(activity, Priority.FOREGROUND, Independence.DEPENDENT);
      this.mDb = activity.mDb;
      this.mPuzzles = activity.mPuzzles;
      this.mSource = activity.mSource;
      this.mName = activity.mName;
    }

    @Override protected Long doInBackground(Void... params) {
      return mDb.addCollection(mName, mSource, mPuzzles);
    }

    @Override protected void onPostExecute(CaptureCollectionActivity activity, Long collectionId) {
      Prefs.instance(activity).setCurrentCollectionAsync(collectionId);
      activity.finish();
      Intent intent = new Intent(activity, SudokuActivity.class);
      activity.startActivity(intent);
    }
  }
}
