package com.coinbase.android.test;

import com.coinbase.api.entity.Address;
import com.coinbase.api.entity.AddressesResponse;
import com.coinbase.api.entity.User;
import com.coinbase.api.entity.UserResponse;

import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

import java.util.ArrayList;
import java.util.List;

public class MockResponses {

  public static AddressesResponse mockAddressesResponse() {
    AddressesResponse response = new AddressesResponse();
    List<Address> addressList = new ArrayList<Address>();

    Address address1 = new Address();
    address1.setAddress("1234asdfbasdf");
    address1.setLabel("Mock Label 1");
    addressList.add(address1);

    Address address2 = new Address();
    address2.setAddress("2234asdfbasdf");
    address2.setLabel("Mock Label 2");
    addressList.add(address2);

    response.setAddresses(addressList);
    response.setCurrentPage(1);
    response.setNumPages(1);
    response.setTotalCount(2);

    return response;
  }

  public static User mockUser() {
    User user = new User();
    user.setName("Test User");
    user.setEmail("user@example.com");
    user.setTimeZone("Pacific Time (US & Canada)");
    user.setNativeCurrency(CurrencyUnit.USD);
    user.setBalance(Money.parse("BTC 1"));
    user.setBuyLevel(1);
    user.setSellLevel(1);
    user.setBuyLimit(Money.parse("USD 3000"));
    user.setSellLimit(Money.parse("USD 3000"));
    return user;
  }

  public static List<CurrencyUnit> supportedCurrencies() {
    return CurrencyUnit.registeredCurrencies();
  }
}
