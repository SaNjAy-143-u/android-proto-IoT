package io.relayr.iotsmartphone.ui.rules;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import java.util.Arrays;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import io.relayr.iotsmartphone.R;
import io.relayr.iotsmartphone.handler.RuleHandler;
import io.relayr.iotsmartphone.storage.Constants;
import io.relayr.iotsmartphone.storage.Storage;
import io.relayr.iotsmartphone.utils.UiHelper;
import io.relayr.java.model.models.schema.NumberSchema;
import io.relayr.java.model.models.schema.ObjectSchema;
import io.relayr.java.model.models.schema.ValueSchema;
import io.relayr.java.model.models.transport.DeviceReading;

import static io.relayr.iotsmartphone.storage.Constants.DeviceType.PHONE;

public class RuleCondition extends LinearLayout {

    @InjectView(R.id.rule_widget_color) View mColorView;

    @InjectView(R.id.rule_widget_icon) ImageView mIconImg;

    @InjectView(R.id.rule_widget_empty_text) TextView mEmptyTv;
    @InjectView(R.id.rule_widget_container) View mContainer;

    @InjectView(R.id.rule_widget_meaning) TextView mMeaningTv;
    @InjectView(R.id.rule_widget_operator) TextView mOperationTv;
    @InjectView(R.id.rule_widget_value) EditText mValueEt;

    private int mColor;
    private FragmentRules.ConditionListener mListener;
    private final List<String> mOperations = Arrays.asList("<", "<=", "==", ">=", ">");

    private int mMin;
    private int mMax;
    private int mValue;
    private String mOperation;
    private DeviceReading mReading;
    private Constants.DeviceType mType;

    public RuleCondition(Context context) {
        this(context, null);
    }

    public RuleCondition(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RuleCondition(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setUp(int color, RuleBuilder rule, int position, FragmentRules.ConditionListener listener) {
        mColor = color;
        mListener = listener;

        if (rule == null) return;

        mType = rule.getConditionType(position);
        if (mType == null) return;

        for (DeviceReading reading : Storage.instance().loadReadings(mType))
            if (reading.getMeaning().equals(rule.getConditionMeaning(position)))
                mReading = reading;
        mOperation = rule.getConditionOperator(position);
        mValue = rule.getConditionValue(position);

        if (isShown()) setConditionValues();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ButterKnife.inject(this, this);

        mColorView.setBackgroundResource(mColor);

        initValueControls();
        if (mType != null) setConditionValues();
    }

    @SuppressWarnings("unused") @OnClick(R.id.rule_widget_remove_btn)
    public void onRemoveClicked() {
        mType = null;
        mReading = null;
        toggleControls(false);
        mListener.removeCondition();
    }

    @SuppressWarnings("unused") @OnClick(R.id.rule_widget_icon)
    public void onIconClicked() {
        final ConditionDialog view = (ConditionDialog) View.inflate(getContext(), R.layout.condition_dialog, null);
        view.setUp(mType, mReading, true);

        new AlertDialog.Builder(getContext(), R.style.AppTheme_DialogOverlay)
                .setView(view)
                .setNegativeButton(getResources().getString(R.string.close), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton(getResources().getString(R.string.save), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if ((mType == null || mReading == null) || !mReading.equals(view.getSelected()) || mType != view.getType()) {
                            mType = view.getType();
                            mReading = (DeviceReading) view.getSelected();

                            toggleControls(true);
                            setInitialValues();
                            setDefaultValues();
                            setConditionValues();
                            mListener.conditionChanged(mType, mReading, mOperation, mValue);
                        }
                    }
                })
                .create()
                .show();
    }

    @SuppressWarnings("unused") @OnClick(R.id.rule_widget_operator)
    public void onOperationClicked() {
        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(getContext(), R.layout.rule_dialog_item);
        arrayAdapter.addAll(mOperations);

        new AlertDialog.Builder(getContext(), R.style.AppTheme_DialogOverlay)
                .setTitle(getContext().getString(R.string.rule_widget_select_operation))
                .setNegativeButton(getContext().getString(R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                .setAdapter(
                        arrayAdapter,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mOperation = mOperations.get(which);
                                mOperationTv.setText(mOperation);
                                mListener.conditionChanged(mType, mReading, mOperation, mValue);
                            }
                        })
                .show();
    }

    private void toggleControls(boolean show) {
        mEmptyTv.setVisibility(show ? GONE : VISIBLE);
        mContainer.setVisibility(show ? VISIBLE : GONE);
        mIconImg.setImageResource(show ? (mType == PHONE ? R.drawable.ic_graphic_phone : R.drawable.ic_graphic_watch) : R.drawable.ic_add_dark);
    }

    private void initValueControls() {
        mValueEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() <= 0) return;
                try {
                    final int value = Integer.parseInt(s.toString());
                    if (value > mMax || value < mMin)
                        mValueEt.setError(getContext().getString(R.string.rule_widget_invalid_out_of_bounds));
                    else mValue = value;
                } catch (NumberFormatException e) {
                    mValueEt.setError(getContext().getString(R.string.rule_widget_invalid_value));
                }
            }

            @Override public void afterTextChanged(Editable s) {}
        });
        mValueEt.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(mValueEt.getWindowToken(), InputMethodManager.RESULT_UNCHANGED_SHOWN);
                    mListener.conditionChanged(mType, mReading, mOperation, mValue);
                    return true;
                }
                return false;
            }
        });
    }

    private void setInitialValues() {
        final ValueSchema valueSchema = mReading.getValueSchema();
        if (valueSchema.isIntegerSchema() || valueSchema.isNumberSchema()) {
            final NumberSchema schema = valueSchema.asNumber();
            setInitialHints(schema);
        } else if (valueSchema.isObjectSchema()) {
            final ObjectSchema schema = valueSchema.asObject();
            final LinkedTreeMap<String, Object> properties = (LinkedTreeMap<String, Object>) schema.getProperties();
            if (properties != null) {
                final Object x = properties.get("x");
                final NumberSchema fromJson = new Gson().fromJson(x.toString(), NumberSchema.class);
                setInitialHints(fromJson);
            }
        }
    }

    public void setInitialHints(NumberSchema schema) {
        mMin = schema.getMin() != null ? schema.getMin().intValue() : 0;
        mMax = schema.getMax() != null ? schema.getMax().intValue() : 100;
        mValueEt.setHint("[" + mMin + ", " + mMax + "]");
    }

    private void setDefaultValues() {
        mValue = (int) (mMax / 2f);
        mOperation = mOperations.get(mOperations.size() - 1);
    }

    private void setConditionValues() {
        setInitialValues();
        toggleControls(true);

        mIconImg.setImageResource(mType == null || mType == PHONE ?
                R.drawable.ic_graphic_phone : R.drawable.ic_graphic_watch);
        mMeaningTv.setText(UiHelper.getNameForMeaning(getContext(), mReading.getMeaning()));
        mOperationTv.setText(mOperation);
        mValueEt.setText("" + mValue);
    }
}