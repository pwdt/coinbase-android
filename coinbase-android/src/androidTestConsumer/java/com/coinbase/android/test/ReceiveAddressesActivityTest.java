package com.coinbase.android.test;

import android.content.ClipboardManager;
import android.content.Context;

import com.coinbase.android.PlatformUtils;
import com.coinbase.android.ReceiveAddressesActivity;
import com.coinbase.android.Utils;
import com.coinbase.api.entity.Address;
import com.coinbase.api.entity.AddressesResponse;

import static com.coinbase.android.test.MockResponses.mockAddressesResponse;
import static com.coinbase.android.test.MockResponses.mockGeneratedAddress;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ReceiveAddressesActivityTest extends MockApiTest {

  private AddressesResponse mockAddressesResponse;

  public ReceiveAddressesActivityTest() {
    super(ReceiveAddressesActivity.class);
  }

  public void setUp() throws Exception {
    super.setUp();

    mockAddressesResponse = mockAddressesResponse();

    doReturn(mockGeneratedAddress()).when(mockCoinbase).generateReceiveAddress();
    doReturn(mockAddressesResponse).when(mockCoinbase).getAddresses();

    startTestActivity();
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }

  public void testAddressesAndLabelsDisplayed() throws Exception {
    getSolo().waitForDialogToClose();
    assertTrue(getSolo().searchText(mockAddressesResponse.getAddresses().get(0).getAddress()));
    assertTrue(getSolo().searchText(mockAddressesResponse.getAddresses().get(1).getAddress()));
    assertTrue(getSolo().searchText(mockAddressesResponse.getAddresses().get(0).getLabel()));
    assertTrue(getSolo().searchText(mockAddressesResponse.getAddresses().get(1).getLabel()));
  }

  public void testCopyToClipboard() throws Exception {
    String address = mockAddressesResponse.getAddresses().get(0).getAddress();
    Utils.setClipboard(getActivity(), "");
    assertEquals("", getClipboardText());
    getSolo().waitForDialogToClose();
    getSolo().clickOnText(address);
    getSolo().waitForText("clipboard");
    assertEquals(address, getClipboardText());
  }

  public void testGenerateNewAddress() throws Exception {
    getSolo().waitForDialogToClose();

    assertFalse(getSolo().searchText("1NewlyGeneratedAddress"));

    Address address = new Address();
    address.setAddress("1NewlyGeneratedAddress");
    address.setLabel("The newly generated address");
    AddressesResponse newAddressesResponse = mockAddressesResponse();
    newAddressesResponse.getAddresses().add(0, address);
    doReturn(newAddressesResponse).when(mockCoinbase).getAddresses();

    getSolo().clickOnMenuItem("Generate");
    getSolo().waitForDialogToClose();
    assertTrue(getSolo().searchText("The newly generated address"));
    assertTrue(getSolo().searchText("1NewlyGeneratedAddress"));

    verify(mockCoinbase, times(1)).generateReceiveAddress();
  }

  protected String getClipboardText() {
    if (PlatformUtils.hasHoneycomb()) {
      android.content.ClipboardManager clipboard =
              (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
      return clipboard.getPrimaryClip().getItemAt(0).getText().toString();
    } else {
      android.text.ClipboardManager clipboard =
              (android.text.ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
      return clipboard.getText().toString();
    }
  }

}
