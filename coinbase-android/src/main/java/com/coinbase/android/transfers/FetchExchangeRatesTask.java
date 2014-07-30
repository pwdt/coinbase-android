package com.coinbase.android.transfers;

import android.content.Context;
import android.widget.Toast;

import com.coinbase.android.ApiTask;
import com.coinbase.android.R;

import java.math.BigDecimal;
import java.util.Map;

public abstract class FetchExchangeRatesTask extends ApiTask<Map<String, BigDecimal>> {
  public FetchExchangeRatesTask(Context context) {
    super(context);
  }

  @Override
  public Map<String, BigDecimal> call() throws Exception {
    return getClient().getExchangeRates();
  }


  @Override
  public void onException(Exception ex) {
    Toast.makeText(context, R.string.transfer_fxrate_failure, Toast.LENGTH_SHORT).show();
    super.onException(ex);
  }
}