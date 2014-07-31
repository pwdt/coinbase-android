package com.coinbase.android.event;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.squareup.otto.Bus;

public class BusModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(Bus.class).in(Scopes.SINGLETON);
  }
}
