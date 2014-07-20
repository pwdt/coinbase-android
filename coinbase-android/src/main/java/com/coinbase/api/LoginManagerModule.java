package com.coinbase.api;

import com.google.inject.AbstractModule;

public class LoginManagerModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(LoginManager.class).to(ProductionLoginManager.class);
  }
}
