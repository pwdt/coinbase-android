package com.coinbase.android.merchant;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.coinbase.android.CoinbaseFragment;
import com.coinbase.android.R;

public class PointOfSaleFragment extends Fragment implements CoinbaseFragment {

  @Override
  public void onSwitchedTo() {

  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    // Inflate the layout for this fragment
    View view = inflater.inflate(R.layout.fragment_transfer, container, false);

    return view;
  }

}
