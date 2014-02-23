package com.coinbase.android.util;

import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.WrapperListAdapter;

/**
 * A ListAdapter which inserts the given View at the given index.
 */
public class InsertedItemListAdapter implements WrapperListAdapter {

  private ListAdapter mWrappedAdapter;
  private View mInsertedView;
  private int mInsertIndex;
  private boolean mInsertedViewVisible = true;

  public InsertedItemListAdapter(ListAdapter adapter, View insertedView, int insertIndex) {
    mWrappedAdapter = adapter;
    mInsertedView = insertedView;
    mInsertIndex = insertIndex;
  }

  public void setInsertedViewVisible(boolean visible) {
    mInsertedViewVisible = visible;
  }

  public void incrementInsertIndex() {
    mInsertIndex++;
  }

  private int numInserted() {
    return mInsertedViewVisible ? 1 : 0;
  }

  @Override
  public ListAdapter getWrappedAdapter() {
    return mWrappedAdapter;
  }

  @Override
  public int getCount() {
    if (mWrappedAdapter.getCount() > mInsertIndex) {
      return mWrappedAdapter.getCount() + numInserted();
    } else {
      return mWrappedAdapter.getCount();
    }
  }

  @Override
  public Object getItem(int position) {
    if (position >= (mInsertIndex + numInserted())) {
      return mWrappedAdapter.getItem(position - numInserted());
    } else if (position < mInsertIndex) {
      return mWrappedAdapter.getItem(position);
    } else {
      return null; // Inserted item
    }
  }

  @Override
  public long getItemId(int position) {
    if (position >= (mInsertIndex + numInserted())) {
      return mWrappedAdapter.getItemId(position - numInserted());
    } else if (position < mInsertIndex) {
      return mWrappedAdapter.getItemId(position);
    } else {
      return -1; // Inserted item
    }
  }

  @Override
  public int getViewTypeCount() {
    return mWrappedAdapter.getViewTypeCount() + 1;
  }

  @Override
  public int getItemViewType(int position) {
    if (position >= (mInsertIndex + numInserted())) {
      return mWrappedAdapter.getItemViewType(position - numInserted());
    } else if (position < mInsertIndex) {
      return mWrappedAdapter.getItemViewType(position);
    } else {
      return mWrappedAdapter.getViewTypeCount(); // Inserted item - use view type not used by wrapped adapter
    }
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    if (position >= (mInsertIndex + numInserted())) {
      return mWrappedAdapter.getView(position - numInserted(), convertView, parent);
    } else if (position < mInsertIndex) {
      return mWrappedAdapter.getView(position, convertView, parent);
    } else {
      return mInsertedView; // Inserted item
    }
  }

  @Override
  public boolean areAllItemsEnabled() {
    return mWrappedAdapter.areAllItemsEnabled();
  }

  @Override
  public boolean isEnabled(int position) {
    if (position >= (mInsertIndex + numInserted())) {
      return mWrappedAdapter.isEnabled(position - numInserted());
    } else if (position < mInsertIndex) {
      return mWrappedAdapter.isEnabled(position);
    } else {
      return true;
    }
  }

  @Override
  public void registerDataSetObserver(DataSetObserver observer) {
    mWrappedAdapter.registerDataSetObserver(observer);
  }

  @Override
  public void unregisterDataSetObserver(DataSetObserver observer) {
    mWrappedAdapter.unregisterDataSetObserver(observer);
  }

  @Override
  public boolean hasStableIds() {
    return mWrappedAdapter.hasStableIds();
  }

  @Override
  public boolean isEmpty() {
    return mWrappedAdapter.isEmpty();
  }
}
