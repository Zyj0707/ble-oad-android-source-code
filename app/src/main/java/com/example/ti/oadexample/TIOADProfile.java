package com.example.ti.oadexample;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import java.util.UUID;


public class TIOADProfile
{
	// Test service
	public static final String TEST_SERVICE = "f000aa64-0451-4000-b000-000000000000";
	public static final String TEST_DATA_CHAR  = "f000aa65-0451-4000-b000-000000000000"; // Test result

	// OAD service
	public static final String OAD_SERVICE = "f000ffc0-0451-4000-b000-000000000000";
	public static final String OAD_IMAGE_NOTIFY_CHAR = "f000ffc1-0451-4000-b000-000000000000";
	public static final String OAD_BLOCK_REQUEST_CHAR = "f000ffc2-0451-4000-b000-000000000000";
	public static final String OAD_IMAGE_STATUS_CHAR = "f000ffc4-0451-4000-b000-000000000000";

	// Connection control service
	public static final String CONNECTION_CONTROL_SERVICE = "f000ccc0-0451-4000-b000-000000000000";
	public static final String REQUEST_CONNECTION_PARAMETERS_CHAR = "f000ccc2-0451-4000-b000-000000000000";

	/**
	 * Verify that the given service is a TIOAD service
	 */
    public static boolean isOadService(BluetoothGattService service)
	{
		if ((service.getUuid().toString().compareTo(OAD_SERVICE)) == 0)
		{
			// Verify the correct characteristics
			BluetoothGattCharacteristic c = service.getCharacteristic(UUID.fromString(OAD_IMAGE_NOTIFY_CHAR));
			if(c == null)
			{
				return false;
			}
			c = service.getCharacteristic(UUID.fromString(OAD_BLOCK_REQUEST_CHAR));
			if(c == null)
			{
				return false;
			}
			c = service.getCharacteristic(UUID.fromString(OAD_IMAGE_STATUS_CHAR));
			if(c == null)
			{
				return false;
			}
			return true;
		}
		return false;
	}

	/**
	 * Verify that the given service is the Test service
	 */
	public static boolean isTestService(BluetoothGattService service) {

		if ((service.getUuid().toString().compareTo(TEST_SERVICE)) == 0)
		{
			// Verify the correct characteristics
			BluetoothGattCharacteristic c = service.getCharacteristic(UUID.fromString(TEST_DATA_CHAR));
			if(c == null)
			{
				return false;
			}
			return true;
		}
		return false;
	}

	/**
	 * Verify that the given service is the Connection Control service
	 */
	public static boolean isConnectionControlService(BluetoothGattService service) {
		if ((service.getUuid().toString().compareTo(CONNECTION_CONTROL_SERVICE)) == 0)
		{
			// Verify the correct characteristics
			BluetoothGattCharacteristic c = service.getCharacteristic(UUID.fromString(REQUEST_CONNECTION_PARAMETERS_CHAR));
			if(c == null)
			{
				return false;
			}
			return true;
		}
		return false;
	}

}
