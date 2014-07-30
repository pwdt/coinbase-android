package com.coinbase.android.test;

import com.coinbase.api.entity.Address;
import com.coinbase.api.entity.AddressResponse;
import com.coinbase.api.entity.AddressesResponse;
import com.coinbase.api.entity.Contact;
import com.coinbase.api.entity.ContactsResponse;
import com.coinbase.api.entity.Quote;
import com.coinbase.api.entity.Response;
import com.coinbase.api.entity.Transfer;
import com.coinbase.api.entity.User;
import com.coinbase.api.entity.UserResponse;

import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.joda.money.CurrencyUnit.USD;

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
    user.setNativeCurrency(USD);
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

  public static AddressResponse mockGeneratedAddress() {
    AddressResponse response = new AddressResponse();
    response.setAddress("1NewlyGeneratedAddress");

    return response;
  }

  public static Quote mockBuyQuote(Money btcAmount) {
    BigDecimal priceOfBtc   = new BigDecimal("590.23");
    BigDecimal subTotal     = btcAmount.getAmount().multiply(priceOfBtc);
    BigDecimal coinbaseFee  = subTotal.multiply(new BigDecimal("0.01"));
    BigDecimal bankFee      = new BigDecimal("0.15");
    BigDecimal total        = subTotal.add(coinbaseFee).add(bankFee);

    Quote result = new Quote();
    result.setSubtotal(Money.of(USD, subTotal, RoundingMode.HALF_EVEN));
    result.setTotal(Money.of(USD, total, RoundingMode.HALF_EVEN));
    result.setFees(new HashMap<String, Money>());
    result.getFees().put("coinbase", Money.of(USD, coinbaseFee, RoundingMode.HALF_EVEN));
    result.getFees().put("bank", Money.of(USD, bankFee, RoundingMode.HALF_EVEN));

    return result;
  }

  public static Quote mockSellQuote(Money btcAmount) {
    BigDecimal priceOfBtc   = new BigDecimal("590.23");
    BigDecimal subTotal     = btcAmount.getAmount().multiply(priceOfBtc);
    BigDecimal coinbaseFee  = subTotal.multiply(new BigDecimal("0.01"));
    BigDecimal bankFee      = new BigDecimal("0.15");
    BigDecimal total        = subTotal.subtract(coinbaseFee).subtract(bankFee);

    Quote result = new Quote();
    result.setSubtotal(Money.of(USD, subTotal, RoundingMode.HALF_EVEN));
    result.setTotal(Money.of(USD, total, RoundingMode.HALF_EVEN));
    result.setFees(new HashMap<String, Money>());
    result.getFees().put("coinbase", Money.of(USD, coinbaseFee, RoundingMode.HALF_EVEN));
    result.getFees().put("bank", Money.of(USD, bankFee, RoundingMode.HALF_EVEN));

    return result;
  }

  public static Transfer mockBuyTransfer(Money amount) {
    Quote buyQuote = mockBuyQuote(amount);
    Transfer result = new Transfer();
    result.setBtc(amount);
    result.setFees(buyQuote.getFees());
    result.setSubtotal(buyQuote.getSubtotal());
    result.setTotal(buyQuote.getTotal());
    result.setType(Transfer.Type.BUY);
    return result;
  }

  public static Transfer mockSellTransfer(Money amount) {
    Quote sellQuote = mockSellQuote(amount);
    Transfer result = new Transfer();
    result.setBtc(amount);
    result.setFees(sellQuote.getFees());
    result.setSubtotal(sellQuote.getSubtotal());
    result.setTotal(sellQuote.getTotal());
    result.setType(Transfer.Type.SELL);
    return result;
  }

  public static Map<String, BigDecimal> mockExchangeRates() {
    HashMap<String, BigDecimal> result = new HashMap<String, BigDecimal>();

    BigDecimal BTC_USD = new BigDecimal("590.23");

    result.put("btc_to_usd", BTC_USD);
    result.put("usd_to_btc", BigDecimal.ONE.divide(BTC_USD, 8, RoundingMode.HALF_EVEN));

    return result;
  }

  public static ContactsResponse mockContacts() {
    ContactsResponse result = newResponse(ContactsResponse.class, 1, 25, 1);

    Contact contact = new Contact();
    contact.setEmail("user@example.com");

    List<Contact> contacts = new ArrayList<Contact>();
    contacts.add(contact);

    result.setContacts(contacts);
    return result;
  }

  public static <T extends Response> T newResponse(Class<T> clazz, int count, int limit, int page) {
    T result;
    try {
      result = clazz.newInstance();
    } catch (Exception ex) {
      throw new AssertionError();
    }

    result.setNumPages(count / limit);
    result.setCurrentPage(page);
    result.setTotalCount(count);

    return result;
  }
}
