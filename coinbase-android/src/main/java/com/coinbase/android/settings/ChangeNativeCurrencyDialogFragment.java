package com.coinbase.android.settings;

import android.app.Dialog;
import android.os.Bundle;

import com.coinbase.android.Constants;
import com.coinbase.android.dialog.SpinnerDialogFragment;
import com.coinbase.api.entity.User;

import org.joda.money.CurrencyUnit;

import java.util.List;

public class ChangeNativeCurrencyDialogFragment extends SpinnerDialogFragment<CurrencyUnit> {
  public static final String CURRENCIES = "ChooseCurrenciesDialogFragment_Currencies";

  protected List<CurrencyUnit> mCurrencies;

  @Override
  public String getOptionDisplayText(CurrencyUnit option) {
    return option.getCurrencyCode();
  }

  @Override
  public CurrencyUnit[] getOptions() {
    return mCurrencies.toArray(new CurrencyUnit[mCurrencies.size()]);
  }

  @Override
  public void onChosenValue(CurrencyUnit newValue) {
    String currencyCode = newValue.getCurrencyCode();

    User user = new User();
    user.setNativeCurrency(newValue);
    UpdateUserTask task = new UpdateUserTask(getActivity(), user, Constants.KEY_ACCOUNT_NATIVE_CURRENCY, currencyCode);
    task.execute();
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    mCurrencies = (List<CurrencyUnit>) getArguments().getSerializable(CURRENCIES);
    return super.onCreateDialog(savedInstanceState);
  }
}
