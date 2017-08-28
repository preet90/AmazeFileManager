package com.amaze.filemanager.ui.dialogs;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.AppCompatSpinner;
import android.text.Editable;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.amaze.filemanager.R;
import com.amaze.filemanager.activities.ThemedActivity;
import com.amaze.filemanager.exceptions.CryptException;
import com.amaze.filemanager.utils.EditTextColorStateUtil;
import com.amaze.filemanager.utils.SimpleTextWatcher;
import com.amaze.filemanager.utils.SmbUtil;
import com.amaze.filemanager.utils.Utils;
import com.amaze.filemanager.utils.color.ColorUsage;
import com.amaze.filemanager.utils.provider.UtilitiesProviderInterface;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;

import jcifs.smb.SmbFile;

/**
 * Created by arpitkh996 on 17-01-2016.
 */
public class SmbConnectDialog extends DialogFragment {

    private UtilitiesProviderInterface utilsProvider;

    private static final String TAG = "SmbConnectDialog";

    public static final String KEY_NAME = "name";
    public static final String KEY_PATH = "path";
    public static final String KEY_EDIT = "edit";
    public static final String KEY_SMB_VERSION = "smb_version";

    public interface SmbConnectionListener {

        /**
         * Callback denoting a new connection been added from dialog
         * @param edit whether we edit existing connection or not
         * @param name name of connection as appears in navigation drawer
         * @param path the full path to the server. Includes an un-encrypted password to support
         *             runtime loading without reloading stuff from database.
         * @param encryptedPath the full path to the server. Includes encrypted password to save in
         *                      database. Later be decrypted at every boot when we read from db entry.
         * @param oldname the old name of connection if we're here to edit
         * @param oldPath the old full path (un-encrypted as we read from existing entry in db, which
         *                we decrypted beforehand).
         * @param smbVersion the version of smb protocol to use
         * @param rememberPassword whether to save password in database or not, if not then a dummy
         *                         non encrypted string will be saved in database so as not to expose
         *                         any vulnerability in encryption
         */
        void addSmbConnection(boolean edit, String name, String path, String encryptedPath,
                           String oldname, String oldPath, SmbUtil.SMB_VERSION smbVersion, boolean rememberPassword);

        /**
         * Callback denoting a connection been deleted from dialog
         * @param name name of connection as in navigation drawer and in database entry
         * @param path the full path to server. Includes an un-encrypted password as we decrypted it
         *             beforehand while reading from database before coming here to delete.
         *             We'll later have to encrypt the password back again in order to match entry
         *             from db and to successfully delete it. If we don't want this behaviour,
         *             then we'll have to not allow duplicate connection name, and delete entry based
         *             on the name only. But that is not supported as of now.
         *             See {@link com.amaze.filemanager.database.UtilsHandler#removeSmbPath(String, String)}
         */
        void deleteSmbConnection(String name, String path);
    }

    private Context context;
    private SmbConnectionListener smbConnectionListener;
    private String emptyAddress, emptyName,invalidDomain,invalidUsername;
    private TextInputLayout connectionTIL, ipTIL, domainTIL, usernameTIL;
    private AppCompatEditText conName, ip, domain, user, pass;
    private AppCompatSpinner smbSpinner;
    private AppCompatCheckBox rememberPasswordCheckBox, anonymousCheckBox;
    private View rootView;
    private SmbUtil.SMB_VERSION smbVersion;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        utilsProvider = (UtilitiesProviderInterface) getActivity();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        final boolean edit = getArguments().getBoolean(KEY_EDIT, false);
        final String path = getArguments().getString(KEY_PATH);
        final String name = getArguments().getString(KEY_NAME);
        smbVersion = SmbUtil.getSmbVersion(getArguments().getInt(KEY_SMB_VERSION));

        context=getActivity();
        emptyAddress = String.format(getString(R.string.cantbeempty),getString(R.string.ip) );
        emptyName = String.format(getString(R.string.cantbeempty),getString(R.string.connectionname) );
        invalidDomain = String.format(getString(R.string.invalid),getString(R.string.domain));
        invalidUsername = String.format(getString(R.string.invalid),getString(R.string.username).toLowerCase());
        if(getActivity() instanceof SmbConnectionListener){
            smbConnectionListener=(SmbConnectionListener)getActivity();
        }
        final SharedPreferences sharedPreferences= PreferenceManager.getDefaultSharedPreferences(context);
        final MaterialDialog.Builder ba3 = new MaterialDialog.Builder(context);
        ba3.title((R.string.smb_con));
        ba3.autoDismiss(false);
        rootView = getActivity().getLayoutInflater().inflate(R.layout.smb_dialog, null);
        connectionTIL = (TextInputLayout)rootView.findViewById(R.id.connectionTIL);
        ipTIL = (TextInputLayout)rootView.findViewById(R.id.ipTIL);
        domainTIL = (TextInputLayout)rootView.findViewById(R.id.domainTIL);
        usernameTIL = (TextInputLayout)rootView.findViewById(R.id.usernameTIL);
        conName = (AppCompatEditText) rootView.findViewById(R.id.connectionET);
        rememberPasswordCheckBox = (AppCompatCheckBox) rootView.findViewById(R.id.checkbox_remember_password);
        smbSpinner = (AppCompatSpinner) rootView.findViewById(R.id.spinner_smb);

        ArrayAdapter<CharSequence> smbVersionsAdapter = ArrayAdapter.createFromResource(getActivity(),
                R.array.smb_versions, android.R.layout.simple_spinner_item);
        smbVersionsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        smbSpinner.setAdapter(smbVersionsAdapter);

        smbSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        smbVersion = SmbUtil.SMB_VERSION.V1;
                        break;
                    case 1:
                        smbVersion = SmbUtil.SMB_VERSION.V2;
                        break;
                    default:
                        smbVersion = SmbUtil.SMB_VERSION.V1;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

                // empty
            }
        });
        // we have versions with offset 1 relative to position
        smbSpinner.setSelection(smbVersion.getVersion()-1);

        conName.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if(conName.getText().toString().length()==0)
                    connectionTIL.setError(emptyName);
                else connectionTIL.setError("");
            }
        });

        ip = (AppCompatEditText) rootView.findViewById(R.id.ipET);
        ip.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if(ip.getText().toString().length()==0)
                    ipTIL.setError(emptyAddress);
                else ipTIL.setError("");
            }
        });

        domain = (AppCompatEditText) rootView.findViewById(R.id.domainET);
        domain.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if(domain.getText().toString().contains(";"))
                    domainTIL.setError(invalidDomain);
                else domainTIL.setError("");
            }
        });

        user = (AppCompatEditText) rootView.findViewById(R.id.usernameET);
        user.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if(user.getText().toString().contains(":"))
                    usernameTIL.setError(invalidUsername);
                else usernameTIL.setError("");
            }
        });

        int accentColor = utilsProvider.getColorPreference().getColor(ColorUsage.ACCENT);

        pass = (AppCompatEditText) rootView.findViewById(R.id.passwordET);

        anonymousCheckBox = (AppCompatCheckBox) rootView.findViewById(R.id.checkBox2);

        TextView help = (TextView) rootView.findViewById(R.id.wanthelp);

        EditTextColorStateUtil.setTint(context, conName, accentColor);
        EditTextColorStateUtil.setTint(context, user, accentColor);
        EditTextColorStateUtil.setTint(context, pass, accentColor);

        Utils.setTint(context, anonymousCheckBox, accentColor);
        Utils.setTint(context, rememberPasswordCheckBox, accentColor);

        help.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int accentColor = ((ThemedActivity) getActivity()).getColorPreference().getColor(ColorUsage.ACCENT);
                GeneralDialogCreation.showSMBHelpDialog(context, accentColor);
            }
        });

        anonymousCheckBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (anonymousCheckBox.isChecked()) {
                    user.setEnabled(false);
                    pass.setEnabled(false);
                } else {
                    user.setEnabled(true);
                    pass.setEnabled(true);

                }
            }
        });

        if (edit) {
            String userp = "", passp = "", ipp = "",domainp = "";
            conName.setText(name);
            try {
                jcifs.Config.registerSmbURLHandler();
                URL a = new URL(path);
                String userinfo = a.getUserInfo();
                if (userinfo != null) {
                    String inf = URLDecoder.decode(userinfo, "UTF-8");
                    int domainDelim = !inf.contains(";") ? 0 : inf.indexOf(';');
                    domainp = inf.substring(0,domainDelim);
                    if(domainp!=null && domainp.length()>0)
                        inf = inf.substring(domainDelim+1);
                    userp = inf.substring(0, inf.indexOf(":"));
                    passp = inf.substring(inf.indexOf(":") + 1, inf.length());
                    domain.setText(domainp);
                    user.setText(userp);

                    if (!passp.equals(SmbUtil.SMB_NO_PASSWORD)) {
                        rememberPasswordCheckBox.setChecked(true);
                        pass.setText("");
                    } else {
                        pass.setText(passp);
                    }
                } else anonymousCheckBox.setChecked(true);
                ipp = a.getHost();
                ip.setText(ipp);
                smbSpinner.setEnabled(false);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }

        } else if(path!=null && path.length()>0) {
            conName.setText(name);
            ip.setText(path);
            user.requestFocus();
        } else {
            conName.setText(R.string.smb_con);
            conName.requestFocus();
        }

        ba3.customView(rootView, true);
        ba3.theme(utilsProvider.getAppTheme().getMaterialDialogTheme());
        ba3.neutralText(R.string.cancel);
        ba3.positiveText(R.string.create);
        if (edit) ba3.negativeText(R.string.delete);
        ba3.positiveColor(accentColor).negativeColor(accentColor).neutralColor(accentColor);
        ba3.onPositive(new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {

                String s[];
                String ipa = ip.getText().toString();
                String con_nam=conName.getText().toString();
                String sDomain = domain.getText().toString();
                String username = user.getText().toString();
                TextInputLayout firstInvalidField  = null;

                if(con_nam==null || con_nam.length()==0) {
                    connectionTIL.setError(emptyName);
                    firstInvalidField = connectionTIL;
                }

                if(ipa==null || ipa.length()==0) {
                    ipTIL.setError(emptyAddress);
                    if(firstInvalidField == null)
                        firstInvalidField = ipTIL;
                }

                if(sDomain.contains(";")) {
                    domainTIL.setError(invalidDomain);
                    if(firstInvalidField == null)
                        firstInvalidField = domainTIL;
                }

                if(username.contains(":")) {
                    usernameTIL.setError(invalidUsername);
                    if(firstInvalidField == null)
                        firstInvalidField = usernameTIL;
                }

                if(firstInvalidField != null) {
                    firstInvalidField.requestFocus();
                    return;
                }

                SmbFile smbFile;
                String domaind = domain.getText().toString();
                if (anonymousCheckBox.isChecked())
                    smbFile = createSMBPath(new String[]{ipa, "", "",domaind}, true);
                else {
                    String useraw = user.getText().toString();
                    String useru = useraw.replaceAll(" ", "\\ ");
                    String passp = pass.getText().toString();
                    smbFile = createSMBPath(new String[]{ipa, useru, passp,domaind}, false);
                }

                if (smbFile == null)
                    return;

                try {

                    s = new String[]{conName.getText().toString(), SmbUtil.getSmbEncryptedPath(getActivity(),
                            smbFile.getPath())};
                } catch (CryptException e) {
                    e.printStackTrace();
                    Toast.makeText(getActivity(), getResources().getString(R.string.error), Toast.LENGTH_LONG).show();
                    return;
                }

                if(smbConnectionListener != null) {
                    // encrypted path means path with encrypted pass
                    smbConnectionListener.addSmbConnection(edit, s[0], smbFile.getPath(), s[1],
                            name, path, smbVersion, rememberPasswordCheckBox.isChecked());
                }
                dismiss();
            }
        });
        ba3.onNegative(new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {

                if(smbConnectionListener!=null){
                    smbConnectionListener.deleteSmbConnection(name, path);
                }

                dismiss();
            }
        });
        ba3.onNeutral(new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                dismiss();
            }
        });

        return ba3.build();
    }

    private SmbFile createSMBPath(String[] auth, boolean anonym) {
        try {
            String yourPeerIP = auth[0], domain = auth[3];

            String path = "smb://"+(android.text.TextUtils.isEmpty(domain) ? ""
                    :( URLEncoder.encode(domain + ";","UTF-8")) )+ (anonym ? "" :
                    (URLEncoder.encode(auth[1], "UTF-8") + ":" + URLEncoder.encode(auth[2], "UTF-8") + "@")) + yourPeerIP + "/";
            SmbFile smbFile = new SmbFile(path);
            return smbFile;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static ColorStateList createEditTextColorStateList(int color) {
        int[][] states = new int[3][];
        int[] colors = new int[3];
        int i = 0;
        states[i] = new int[]{-android.R.attr.state_enabled};
        colors[i] = Color.parseColor("#f6f6f6");
        i++;
        states[i] = new int[]{-android.R.attr.state_pressed, -android.R.attr.state_focused};
        colors[i] = Color.parseColor("#666666");
        i++;
        states[i] = new int[]{};
        colors[i] = color;
        return new ColorStateList(states, colors);
    }

    private static void setTint(EditText editText, int color) {
        if (Build.VERSION.SDK_INT >= 21) return;
        ColorStateList editTextColorStateList = createEditTextColorStateList(color);
        if (editText instanceof AppCompatEditText) {
            ((AppCompatEditText) editText).setSupportBackgroundTintList(editTextColorStateList);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            editText.setBackgroundTintList(editTextColorStateList);
        }
    }
}