package com.coinbase.android.settings;

import android.app.Dialog;
import android.os.Bundle;

import com.coinbase.android.Constants;
import com.coinbase.android.dialog.SpinnerDialogFragment;
import com.coinbase.api.entity.User;

public class ChangeTimezoneDialogFragment extends SpinnerDialogFragment<Timezone> {
  public static final String TIMEZONES = "ChooseTimezoneDialogFragment_Timezones";

  protected Timezone[] mTimezones;

  @Override
  public String getOptionDisplayText(Timezone option) {
    return option.getDisplayText();
  }

  @Override
  public Timezone[] getOptions() {
    return mTimezones;
  }

  @Override
  public void onChosenValue(Timezone newValue) {
    String timezone = newValue.getTimezone();

    User user = new User();
    user.setTimeZone(timezone);
    UpdateUserTask task = new UpdateUserTask(getActivity(), user, Constants.KEY_ACCOUNT_TIME_ZONE, timezone);
    task.execute();
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    mTimezones = (Timezone[]) getArguments().getSerializable(TIMEZONES);
    return super.onCreateDialog(savedInstanceState);
  }
}
