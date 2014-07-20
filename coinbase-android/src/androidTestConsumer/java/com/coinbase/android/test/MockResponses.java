package com.coinbase.android.test;

import com.coinbase.api.entity.Address;
import com.coinbase.api.entity.AddressesResponse;

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

}
