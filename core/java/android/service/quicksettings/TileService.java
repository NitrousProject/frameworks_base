/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.service.quicksettings;

import android.Manifest;
import android.app.Dialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.view.WindowManager;

/**
 * A TileService provides the user a tile that can be added to Quick Settings.
 * Quick Settings is a space provided that allows the user to change settings and
 * take quick actions without leaving the context of their current app.
 *
 * <p>The lifecycle of a TileService is different from some other services in
 * that it may be unbound during parts of its lifecycle.  Any of the following
 * lifecycle events can happen indepently in a separate binding/creation of the
 * service.</p>
 *
 * <ul>
 * <li>When a tile is added by the user its TileService will be bound to and
 * {@link #onTileAdded()} will be called.</li>
 *
 * <li>When a tile should be up to date and listing will be indicated by
 * {@link #onStartListening()} and {@link #onStopListening()}.</li>
 *
 * <li>When the user removes a tile from Quick Settings {@link #onTileRemoved()}
 * will be called.</li>
 * </ul>
 * <p>TileService will be detected by tiles that match the {@value #ACTION_QS_TILE}
 * and require the permission "android.permission.BIND_QUICK_SETTINGS_TILE".
 * The label and icon for the service will be used as the default label and
 * icon for the tile. Here is an example TileService declaration.</p>
 * <pre class="prettyprint">
 * {@literal
 * <service
 *     android:name=".MyQSTileService"
 *     android:label="@string/my_default_tile_label"
 *     android:icon="@drawable/my_default_icon_label"
 *     android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
 *     <intent-filter>
 *         <action android:name="android.service.quicksettings.action.QS_TILE" />
 *     </intent-filter>
 * </service>}
 * </pre>
 *
 * @see Tile Tile for details about the UI of a Quick Settings Tile.
 */
public class TileService extends Service {

    /**
     * Action that identifies a Service as being a TileService.
     */
    public static final String ACTION_QS_TILE = "android.service.quicksettings.action.QS_TILE";

    /**
     * The tile mode hasn't been set yet.
     * @hide
     */
    public static final int TILE_MODE_UNSET = 0;

    /**
     * Constant to be returned by {@link #onTileAdded}.
     * <p>
     * Passive mode is the default mode for tiles.  The System will tell the tile
     * when it is most important to update by putting it in the listening state.
     */
    public static final int TILE_MODE_PASSIVE = 1;

    /**
     * Constant to be returned by {@link #onTileAdded}.
     * <p>
     * Active mode is for tiles which already listen and keep track of their state in their
     * own process.  These tiles may request to send an update to the System while their process
     * is alive using {@link #requestListeningState}.  The System will only bind these tiles
     * on its own when a click needs to occur.
     */
    public static final int TILE_MODE_ACTIVE = 2;

    /**
     * Used to notify SysUI that Listening has be requested.
     * @hide
     */
    public static final String ACTION_REQUEST_LISTENING
            = "android.service.quicksettings.action.REQUEST_LISTENING";

    /**
     * @hide
     */
    public static final String EXTRA_COMPONENT = "android.service.quicksettings.extra.COMPONENT";

    private final H mHandler = new H(Looper.getMainLooper());

    private boolean mListening = false;
    private Tile mTile;
    private IBinder mToken;
    private IQSService mService;

    @Override
    public void onDestroy() {
        if (mListening) {
            onStopListening();
            mListening = false;
        }
        super.onDestroy();
    }

    /**
     * Called when the user adds this tile to Quick Settings.
     * <p/>
     * Note that this is not guaranteed to be called between {@link #onCreate()}
     * and {@link #onStartListening()}, it will only be called when the tile is added
     * and not on subsequent binds.
     *
     * @see #TILE_MODE_PASSIVE
     * @see #TILE_MODE_ACTIVE
     */
    public int onTileAdded() {
        return TILE_MODE_PASSIVE;
    }

    /**
     * Called when the user removes this tile from Quick Settings.
     */
    public void onTileRemoved() {
    }

    /**
     * Called when this tile moves into a listening state.
     * <p/>
     * When this tile is in a listening state it is expected to keep the
     * UI up to date.  Any listeners or callbacks needed to keep this tile
     * up to date should be registered here and unregistered in {@link #onStopListening()}.
     *
     * @see #getQsTile()
     * @see Tile#updateTile()
     */
    public void onStartListening() {
    }

    /**
     * Called when this tile moves out of the listening state.
     */
    public void onStopListening() {
    }

    /**
     * Called when the user clicks on this tile.
     */
    public void onClick() {
    }

    /**
     * Used to show a dialog.
     *
     * This will collapse the Quick Settings panel and show the dialog.
     *
     * @param dialog Dialog to show.
     */
    public final void showDialog(Dialog dialog) {
        dialog.getWindow().getAttributes().token = mToken;
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_QS_DIALOG);
        dialog.show();
        try {
            mService.onShowDialog(mTile);
        } catch (RemoteException e) {
        }
    }

    /**
     * Gets the {@link Tile} for this service.
     * <p/>
     * This tile may be used to get or set the current state for this
     * tile. This tile is only valid for updates between {@link #onStartListening()}
     * and {@link #onStopListening()}.
     */
    public final Tile getQsTile() {
        return mTile;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new IQSTileService.Stub() {
            @Override
            public void setQSService(IQSService service) throws RemoteException {
                mHandler.obtainMessage(H.MSG_SET_SERVICE, service).sendToTarget();
            }

            @Override
            public void setQSTile(Tile tile) throws RemoteException {
                mHandler.obtainMessage(H.MSG_SET_TILE, tile).sendToTarget();
            }

            @Override
            public void onTileRemoved() throws RemoteException {
                mHandler.sendEmptyMessage(H.MSG_TILE_REMOVED);
            }

            @Override
            public void onTileAdded() throws RemoteException {
                mHandler.sendEmptyMessage(H.MSG_TILE_ADDED);
            }

            @Override
            public void onStopListening() throws RemoteException {
                mHandler.sendEmptyMessage(H.MSG_STOP_LISTENING);
            }

            @Override
            public void onStartListening() throws RemoteException {
                mHandler.sendEmptyMessage(H.MSG_START_LISTENING);
            }

            @Override
            public void onClick(IBinder wtoken) throws RemoteException {
                mHandler.obtainMessage(H.MSG_TILE_CLICKED, wtoken).sendToTarget();
            }
        };
    }

    private class H extends Handler {
        private static final int MSG_SET_TILE = 1;
        private static final int MSG_START_LISTENING = 2;
        private static final int MSG_STOP_LISTENING = 3;
        private static final int MSG_TILE_ADDED = 4;
        private static final int MSG_TILE_REMOVED = 5;
        private static final int MSG_TILE_CLICKED = 6;
        private static final int MSG_SET_SERVICE = 7;

        public H(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SET_SERVICE:
                    mService = (IQSService) msg.obj;
                    if (mTile != null) {
                        mTile.setService(mService);
                    }
                    break;
                case MSG_SET_TILE:
                    mTile = (Tile) msg.obj;
                    if (mService != null && mTile != null) {
                        mTile.setService(mService);
                    }
                    break;
                case MSG_TILE_ADDED:
                    int mode = TileService.this.onTileAdded();
                    if (mService == null) {
                        return;
                    }
                    try {
                        mService.setTileMode(new ComponentName(TileService.this,
                                TileService.this.getClass()), mode);
                    } catch (RemoteException e) {
                    }
                    break;
                case MSG_TILE_REMOVED:
                    if (mListening) {
                        mListening = false;
                        TileService.this.onStopListening();
                    }
                    TileService.this.onTileRemoved();
                    break;
                case MSG_STOP_LISTENING:
                    if (mListening) {
                        mListening = false;
                        TileService.this.onStopListening();
                    }
                    break;
                case MSG_START_LISTENING:
                    if (!mListening) {
                        mListening = true;
                        TileService.this.onStartListening();
                    }
                    break;
                case MSG_TILE_CLICKED:
                    mToken = (IBinder) msg.obj;
                    TileService.this.onClick();
                    break;
            }
        }
    }

    /**
     * Requests that a tile be put in the listening state so it can send an update.
     *
     * This method is only applicable to tiles that return {@link #TILE_MODE_ACTIVE} from
     * {@link #onTileAdded()}, and will do nothing otherwise.
     */
    public static final void requestListeningState(Context context, ComponentName component) {
        Intent intent = new Intent(ACTION_REQUEST_LISTENING);
        intent.putExtra(EXTRA_COMPONENT, component);
        context.sendBroadcast(intent, Manifest.permission.BIND_QUICK_SETTINGS_TILE);
    }
}
