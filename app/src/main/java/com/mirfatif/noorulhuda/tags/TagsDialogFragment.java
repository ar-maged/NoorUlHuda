package com.mirfatif.noorulhuda.tags;

import static com.mirfatif.noorulhuda.prefs.MySettings.SETTINGS;
import static com.mirfatif.noorulhuda.tags.TagDialogFragment.CREATED_NEW;
import static com.mirfatif.noorulhuda.tags.TagDialogFragment.TAG_ID;
import static com.mirfatif.noorulhuda.util.Utils.getArNum;
import static com.mirfatif.noorulhuda.util.Utils.setTooltip;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog.Builder;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.appcompat.widget.SearchView.OnQueryTextListener;
import com.mirfatif.noorulhuda.R;
import com.mirfatif.noorulhuda.databinding.DialogListViewBinding;
import com.mirfatif.noorulhuda.db.AayahEntity;
import com.mirfatif.noorulhuda.db.SurahEntity;
import com.mirfatif.noorulhuda.db.TagAayahsEntity;
import com.mirfatif.noorulhuda.db.TagEntity;
import com.mirfatif.noorulhuda.quran.MainActivity;
import com.mirfatif.noorulhuda.tags.TagsAdapter.TagCheckboxCallback;
import com.mirfatif.noorulhuda.ui.dialog.AlertDialogFragment;
import com.mirfatif.noorulhuda.ui.dialog.MyBaseAdapter.DialogListCallback;
import com.mirfatif.noorulhuda.util.Utils;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class TagsDialogFragment extends AppCompatDialogFragment {

  public static final String PARENT_FRAG_TAG = "ALL_TAGS";
  public static final String CHILD_FRAG_TAG = "TAG";
  public static final String AAYAH_ID = "AAYAH_ID";
  private MainActivity mA;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    mA = (MainActivity) getActivity();
  }

  private DialogListViewBinding mB;
  private TagsAdapter mAdapter;
  private int mAayahId = -1;

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    mB = DialogListViewBinding.inflate(getLayoutInflater());
    setTooltip(mB.addNewButton);
    setTooltip(mB.crossV);

    Bundle args = getArguments();
    if (args != null) {
      mAayahId = args.getInt(AAYAH_ID, mAayahId);
    }

    mB.topRow.setVisibility(View.VISIBLE);
    mB.searchV.setOnQueryTextListener(new SearchTextListener());
    mB.addNewButton.setOnClickListener(v -> Utils.runBg(this::addNewTag));

    setEmptyViewText();
    mB.listV.setEmptyView(mB.emptyV);

    mAdapter = new TagsAdapter(mA, new DialogListCbImpl());
    Utils.runBg(this::submitList);
    mB.listV.setAdapter(mAdapter);

    if (mAayahId >= 0) {
      mB.titleCont.setVisibility(View.VISIBLE);
      mB.surahNameV.setTypeface(SETTINGS.getTypeface());
      mB.aayahTextV.setTypeface(SETTINGS.getTypeface());
      mB.crossV.setOnClickListener(
          v -> {
            mB.titleCont.setVisibility(View.GONE);
            mAayahId = -1;
            mAdapter.setAayahId(-1, null);
            Utils.runBg(this::submitList);
          });
      mAdapter.setAayahId(mAayahId, new TagCheckboxCbImpl());
      Utils.runBg(this::setTitle);
    }

    Builder builder = new Builder(mA).setTitle(R.string.tags_menu_item).setView(mB.getRoot());
    return AlertDialogFragment.onCreateDialog(builder.create());
  }

  private void setTitle() {
    AayahEntity aayah = SETTINGS.getQuranDb().getAayahEntity(mAayahId);
    SurahEntity surah;
    if (aayah != null && (surah = SETTINGS.getMetaDb().getSurah(aayah.surahNum)) != null) {
      Utils.runUi(
          () -> {
            mB.surahNameV.setText(getString(R.string.surah_name, surah.name));
            mB.aayahTextV.setText(aayah.text);
            mB.aayahNumV.setText(getArNum(aayah.aayahNum));
          });
    }
  }

  private final List<TagEntity> mTags = new ArrayList<>();

  void submitList() {
    synchronized (mTags) {
      mTags.clear();
      mTags.addAll(SETTINGS.getTagsDb().getTags());
      mTags.sort((t1, t2) -> Long.compare(t2.timeStamp, t1.timeStamp));
    }
    for (TagEntity tag : new ArrayList<>(mTags)) {
      tag.aayahIds.addAll(SETTINGS.getTagAayahsDb().getAayahIds(tag.id));
      tag.surahCount = new HashSet<>(SETTINGS.getQuranDb().getSurahs(tag.aayahIds)).size();
    }
    Utils.runUi(
        () -> {
          handleSearchQuery();
          setEmptyViewText();
        });
  }

  private void setEmptyViewText() {
    if (!mTags.isEmpty()) {
      mB.emptyV.setText(R.string.no_matches);
    } else if (mAayahId < 0) {
      mB.emptyV.setText(R.string.long_press_to_tag);
    } else {
      mB.emptyV.setText(R.string.press_plus_create_tag);
    }
  }

  private void handleSearchQuery() {
    String query = mB.searchV.getQuery() == null ? null : mB.searchV.getQuery().toString();
    List<TagEntity> tags = new ArrayList<>();
    if (query == null || query.length() == 0) {
      tags.addAll(mTags);
    } else {
      query = query.toUpperCase();
      for (TagEntity tag : mTags) {
        if (tag.title.toUpperCase().contains(query)
            || (tag.desc != null && tag.desc.toUpperCase().contains(query))) {
          tags.add(tag);
        }
      }
    }
    Utils.runUi(() -> mAdapter.submitList(tags));
  }

  private void addNewTag() {
    TagEntity tag = new TagEntity();
    tag.title =
        new SimpleDateFormat("dd-MMM-yy HH:mm:ss", Locale.getDefault())
            .format(System.currentTimeMillis());
    tag.id = (int) SETTINGS.getTagsDb().create(tag);
    submitList();
    Utils.runUi(() -> openTag(tag, true));
  }

  private void openTag(TagEntity tag, boolean isNew) {
    TagDialogFragment frag = new TagDialogFragment();
    Bundle args = new Bundle();
    args.putInt(TAG_ID, tag.id);
    if (isNew) {
      args.putBoolean(CREATED_NEW, true);
    }
    frag.setArguments(args);
    frag.showNow(getParentFragmentManager(), CHILD_FRAG_TAG);
  }

  private class DialogListCbImpl implements DialogListCallback {

    @Override
    public void onItemSelect(int pos) {
      TagEntity tag = mAdapter.getItem(pos);
      if (tag != null) {
        openTag(tag, false);
      }
    }

    @Override
    public void onDelete(int pos) {
      TagEntity tag = mAdapter.getItem(pos);
      if (tag != null) {
        mTags.remove(tag);
        setEmptyViewText();
        Utils.runBg(
            () -> {
              SETTINGS.getTagsDb().remove(tag);
              SETTINGS.getTagAayahsDb().remove(tag.id);
            });
      }
    }
  }

  private class TagCheckboxCbImpl implements TagCheckboxCallback {

    @Override
    public void checkboxChanged(int pos, boolean checked) {
      TagEntity tag = mAdapter.getItem(pos);
      if (tag != null) {
        Utils.runBg(
            () -> {
              SETTINGS.getTagAayahsDb().remove(tag.id, mAayahId);
              if (checked) {
                SETTINGS.getTagAayahsDb().create(new TagAayahsEntity(tag.id, mAayahId));
              }
              submitList();
            });
      }
    }
  }

  private class SearchTextListener implements OnQueryTextListener {

    @Override
    public boolean onQueryTextSubmit(String query) {
      handleSearchQuery();
      return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
      handleSearchQuery();
      return true;
    }
  }
}