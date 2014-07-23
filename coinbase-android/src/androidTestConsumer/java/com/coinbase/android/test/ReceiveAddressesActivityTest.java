package com.coinbase.android.test;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import com.coinbase.android.AboutActivity;
import com.coinbase.android.PlatformUtils;
import com.coinbase.android.ReceiveAddressesActivity;
import com.coinbase.android.Utils;
import com.coinbase.api.entity.AddressesResponse;
import com.robotium.solo.Solo;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doReturn;

import static com.coinbase.android.test.MockResponses.*;

public class ReceiveAddressesActivityTest extends MockApiTest {

  private AddressesResponse mockAddressesResponse;

  public ReceiveAddressesActivityTest() {
    super(ReceiveAddressesActivity.class);
  }

  public void setUp() throws Exception {
    super.setUp();

    mockAddressesResponse = mockAddressesResponse();

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
