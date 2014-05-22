package com.coinbase.android.ui;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.coinbase.android.AccountsFragment;
import com.coinbase.android.BuildConfig;
import com.coinbase.android.BuildType;
import com.coinbase.android.Constants;
import com.coinbase.android.FontManager;
import com.coinbase.android.MainActivity;
import com.coinbase.android.R;
import com.coinbase.android.Utils;
import com.coinbase.android.event.SectionSelectedEvent;
import com.coinbase.android.event.UserDataUpdatedEvent;
import com.squareup.otto.Subscribe;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sliding drawer that appears at the side of the screen.
 */
public class SlidingDrawerFragment extends Fragment {

  private class SectionsListItem {
    public String[] text;
    public int layoutId;
    public Mintent mintent;
    public SectionsListItem(String text, int layoutId, Mintent mintent) {
      this.text = new String[] { text };
      this.layoutId = layoutId;
      this.mintent = mintent;
    }
    public SectionsListItem(int text, int layoutId, Mintent mintent) {
      this.text = new String[] { getString(text) };
      this.layoutId = layoutId;
      this.mintent = mintent;
    }

    /** Can be overridden if a fragment has multiple parts, some selected some not */
    public boolean isSelected() {
      return mintent.section == ((MainActivity) getActivity()).getSelectedSection();
    }
  }

  private class SectionsListAdapter extends BaseAdapter {

    private List<SectionsListItem> items;

    public SectionsListAdapter() {
      buildData();
    }

    @Override
    public void notifyDataSetChanged() {
      buildData();
      super.notifyDataSetChanged();
    }

    private void buildData() {

      items = new ArrayList<SectionsListItem>();

      if (BuildConfig.type == BuildType.CONSUMER) {

        items.add(new SectionsListItem(R.string.title_transactions, R.layout.drawer_item_large, Mintent.TRANSACTIONS));
        items.add(new SectionsListItem(R.string.title_transfer, R.layout.drawer_item_large, Mintent.SEND_REQUEST));
        items.add(new SectionsListItem(R.string.title_buysell, R.layout.drawer_item_large, Mintent.BUY_SELL));
        items.add(new SectionsListItem(R.string.title_account, R.layout.drawer_item_large, Mintent.SETTINGS));
      } else {
        items.add(new SectionsListItem(R.string.title_point_of_sale, R.layout.drawer_item_large, Mintent.POINT_OF_SALE));
      }
    }

    @Override
    public int getCount() {

      return items.size();
    }

    @Override
    public Object getItem(int position) {
      return items.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

      SectionsListItem item = (SectionsListItem) getItem(position);

      // Do NOT use convertView - view recycling is not used here so we can dynamically specify the layout
      View view = getActivity().getLayoutInflater().inflate(item.layoutId, parent, false);

      for (int i = 0; i < item.text.length; i++) {
        int id = Resources.getSystem().getIdentifier("text" + (i + 1), "id", "android");
        TextView tview = (TextView) view.findViewById(id);

        tview.setText(item.text[i]);
        tview.setTypeface(FontManager.getFont(getActivity(), "Roboto-" + (item.isSelected() ? "Bold" : "Light")));
      }

      view.setEnabled(item.isSelected());

      return view;
    }

  }

  private class LoadAvatarTask extends AsyncTask<String, Void, Bitmap> {

    @Override
    protected Bitmap doInBackground(String... arg0) {

      try {
        String url = String.format("https://secure.gravatar.com/avatar/%1$s?s=100&d=https://coinbase.com/assets/avatar.png",
                Utils.md5(arg0[0].toLowerCase(Locale.CANADA).trim()));
        Log.i("Coinbase", "Loading avatar " + url);
        return BitmapFactory.decodeStream(new URL(url).openStream());
      } catch (Exception e) {
        Log.i("Coinbase", "Could not load avatar!");
        e.printStackTrace();
        return null;
      }
    }

    @Override
    protected void onPostExecute(Bitmap result) {
      if(result != null) {
        ((ImageView) mProfileView.findViewById(R.id.drawer_profile_avatar)).setImageDrawable(new AvatarDrawable(result));
      }
    }
  }

  View mProfileView;
  SectionsListAdapter mAdapter;

  private void createProfileView() {
    mProfileView = View.inflate(getActivity(), R.layout.activity_main_drawer_profile, null);
    ImageView photo = (ImageView) mProfileView.findViewById(R.id.drawer_profile_avatar);

    photo.setImageDrawable(new AvatarDrawable(BitmapFactory.decodeResource(getResources(), R.drawable.no_avatar)));

    refreshProfileView();
  }

  public void refreshProfileView() {

    TextView name = (TextView) mProfileView.findViewById(R.id.drawer_profile_name);
    TextView email = (TextView) mProfileView.findViewById(R.id.drawer_profile_account);

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    String emailText = prefs.getString(String.format(Constants.KEY_ACCOUNT_NAME, activeAccount), "");
    name.setText(prefs.getString(String.format(Constants.KEY_ACCOUNT_FULL_NAME, activeAccount), null));

    boolean emailChanged = !emailText.equals(email.getText().toString());
    if (emailChanged) {
      email.setText(emailText);
      new LoadAvatarTask().execute(emailText);
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    ListView view = (ListView) inflater.inflate(R.layout.fragment_sliding_drawer, null);
    mAdapter = new SectionsListAdapter();
    view.setAdapter(mAdapter);
    view.setOnItemClickListener(new AdapterView.OnItemClickListener() {

      @Override
      public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                              long arg3) {

        if(arg2 == 0) {
          // Switch account
          new AccountsFragment().show(getFragmentManager(), "accounts");
          return;
        }

        SectionsListItem fragment = (SectionsListItem) arg0.getAdapter().getItem(arg2);
        if(fragment != null) {
          ((MainActivity) getActivity()).switchTo(fragment.mintent);
        }
      }
    });

    // Profile
    createProfileView();
    view.addHeaderView(mProfileView);

    return view;
  }

  @Subscribe
  public void userDataUpdated(UserDataUpdatedEvent event) {
    refreshProfileView();
  }

  @Subscribe
  public void sectionSelected(SectionSelectedEvent event) {
    mAdapter.notifyDataSetChanged();
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Utils.bus().register(this);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Utils.bus().unregister(this);
  }
}
