/*
 *				Twidere - Twitter client for Android
 * 
 * Copyright (C) 2012 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.twidere.fragment;

import static android.text.TextUtils.isEmpty;
import static org.mariotaku.twidere.util.Utils.addIntentToMenu;
import static org.mariotaku.twidere.util.Utils.findUserList;
import static org.mariotaku.twidere.util.Utils.getAccountColor;
import static org.mariotaku.twidere.util.Utils.getBiggerTwitterProfileImage;
import static org.mariotaku.twidere.util.Utils.getTwitterInstance;
import static org.mariotaku.twidere.util.Utils.isMyActivatedAccount;
import static org.mariotaku.twidere.util.Utils.openUserListMembers;
import static org.mariotaku.twidere.util.Utils.openUserListSubscribers;
import static org.mariotaku.twidere.util.Utils.openUserListTimeline;
import static org.mariotaku.twidere.util.Utils.openUserProfile;
import static org.mariotaku.twidere.util.Utils.parseString;

import org.mariotaku.popupmenu.PopupMenu;
import org.mariotaku.popupmenu.PopupMenu.OnMenuItemClickListener;
import org.mariotaku.twidere.R;
import org.mariotaku.twidere.adapter.AutoCompleteAdapter;
import org.mariotaku.twidere.adapter.ListActionAdapter;
import org.mariotaku.twidere.model.ListAction;
import org.mariotaku.twidere.model.Panes;
import org.mariotaku.twidere.model.ParcelableUser;
import org.mariotaku.twidere.model.ParcelableUserList;
import org.mariotaku.twidere.util.AsyncTwitterWrapper;
import org.mariotaku.twidere.util.ImageLoaderWrapper;
import org.mariotaku.twidere.util.OnLinkClickHandler;
import org.mariotaku.twidere.util.TwidereLinkify;
import org.mariotaku.twidere.view.ColorLabelRelativeLayout;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.UserList;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.InputFilter;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class UserListDetailsFragment extends BaseListFragment implements OnClickListener, OnLongClickListener,
		OnItemClickListener, OnItemLongClickListener, OnMenuItemClickListener,
		LoaderCallbacks<UserListDetailsFragment.Response<UserList>>, Panes.Right {

	private ImageLoaderWrapper mProfileImageLoader;
	private AsyncTwitterWrapper mTwitterWrapper;

	private ImageView mProfileImageView;
	private TextView mListNameView, mUserNameView, mDescriptionView, mErrorMessageView;
	private View mListContainer, mErrorRetryContainer;
	private ColorLabelRelativeLayout mProfileContainer;
	private View mNameContainer, mProfileImageContainer, mDescriptionContainer;
	private Button mFollowMoreButton, mRetryButton;
	private ListView mListView;
	private View mHeaderView;

	private ListActionAdapter mAdapter;

	private final DialogFragment mAddMemberDialogFragment = new AddMemberDialogFragment(),
			mEditUserListDialogFragment = new EditUserListDialogFragment();
	private PopupMenu mPopupMenu;

	private long mAccountId, mUserId;
	private UserList mUserList = null;
	private int mUserListId;
	private String mUserName, mUserScreenName, mListName;
	private boolean mHiResProfileImage;

	private final BroadcastReceiver mStatusReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			if (BROADCAST_USER_LIST_DETAILS_UPDATED.equals(action)) {
				if (intent.getIntExtra(INTENT_KEY_LIST_ID, -1) == mUserListId
						&& intent.getBooleanExtra(INTENT_KEY_SUCCEED, false)) {
					reloadUserListInfo();
				}
			} else if (BROADCAST_USER_LIST_SUBSCRIPTION_CHANGED.equals(action)) {
				if (intent.getIntExtra(INTENT_KEY_LIST_ID, -1) == mUserListId
						&& intent.getBooleanExtra(INTENT_KEY_SUCCEED, false)) {
					reloadUserListInfo();
				}
			}
		}
	};

	public void changeUserList(final long account_id, final UserList user_list) {
		if (user_list == null || getActivity() == null || !isMyActivatedAccount(getActivity(), account_id)) return;
		getLoaderManager().destroyLoader(0);
		final User user = user_list.getUser();
		if (user == null) return;
		final boolean is_my_activated_account = isMyActivatedAccount(getActivity(), user_list.getId());
		mErrorRetryContainer.setVisibility(View.GONE);
		mAccountId = account_id;
		mUserListId = user_list.getId();
		mUserName = user.getName();
		mUserId = user.getId();
		mUserScreenName = user.getScreenName();
		mListName = user_list.getName();
		mProfileContainer.drawRight(getAccountColor(getActivity(), account_id));
		mListNameView.setText("@" + mUserScreenName + "/" + mListName);
		mUserNameView.setText(mUserName);
		final String description = user_list.getDescription();
		mDescriptionContainer
				.setVisibility(is_my_activated_account || !isEmpty(description) ? View.VISIBLE : View.GONE);
		mDescriptionContainer.setOnLongClickListener(this);
		mDescriptionView.setText(description);
		final TwidereLinkify linkify = new TwidereLinkify(new OnLinkClickHandler(getActivity()), getActivity(), true);
		linkify.applyAllLinks(mDescriptionView, account_id, false);
		mDescriptionView.setMovementMethod(LinkMovementMethod.getInstance());
		final String profile_image_url_string = parseString(user.getProfileImageUrlHttps());
		final boolean hires_profile_image = getResources().getBoolean(R.bool.hires_profile_image);
		mProfileImageLoader
				.displayProfileImage(mProfileImageView,
						hires_profile_image ? getBiggerTwitterProfileImage(profile_image_url_string)
								: profile_image_url_string);
		mUserList = user_list;
		if (mUserId == mAccountId) {
			mFollowMoreButton.setText(R.string.more);
			mFollowMoreButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.expander_open_holo, 0);
		} else {
			mFollowMoreButton.setText(user_list.isFollowing() ? R.string.unfollow : R.string.follow);
			mFollowMoreButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
		}
		mAdapter.notifyDataSetChanged();
	}

	public void getUserListInfo(final boolean init, final long account_id, final int list_id, final String list_name,
			final long user_id, final String screen_name) {
		mAccountId = account_id;
		mUserListId = list_id;
		mUserName = screen_name;
		getLoaderManager().destroyLoader(0);
		if (!isMyActivatedAccount(getActivity(), mAccountId)) {
			mListContainer.setVisibility(View.GONE);
			mErrorRetryContainer.setVisibility(View.GONE);
			return;
		}

		if (list_id > 0 || list_name != null && (user_id > 0 || screen_name != null)) {
		} else {
			mListContainer.setVisibility(View.GONE);
			mErrorRetryContainer.setVisibility(View.GONE);
			return;
		}
		final Bundle args = new Bundle();
		args.putLong(INTENT_KEY_ACCOUNT_ID, account_id);
		args.putLong(INTENT_KEY_USER_ID, user_id);
		args.putInt(INTENT_KEY_LIST_ID, list_id);
		args.putString(INTENT_KEY_LIST_NAME, list_name);
		args.putString(INTENT_KEY_SCREEN_NAME, screen_name);
		if (init) {
			getLoaderManager().initLoader(0, args, this);
		} else {
			getLoaderManager().restartLoader(0, args, this);
		}
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		mTwitterWrapper = getApplication().getTwitterWrapper();
		super.onActivityCreated(savedInstanceState);
		final Bundle args = getArguments();
		long account_id = -1, user_id = -1;
		int list_id = -1;
		String screen_name = null, list_name = null;
		if (args != null) {
			account_id = args.getLong(INTENT_KEY_ACCOUNT_ID, -1);
			user_id = args.getLong(INTENT_KEY_USER_ID, -1);
			list_id = args.getInt(INTENT_KEY_LIST_ID, -1);
			list_name = args.getString(INTENT_KEY_LIST_NAME);
			screen_name = args.getString(INTENT_KEY_SCREEN_NAME);
		}
		mHiResProfileImage = getResources().getBoolean(R.bool.hires_profile_image);
		mProfileImageLoader = getApplication().getImageLoaderWrapper();
		mAdapter = new ListActionAdapter(getActivity());
		mAdapter.add(new ListTimelineAction(1));
		mAdapter.add(new ListMembersAction(2));
		mAdapter.add(new ListSubscribersAction(3));
		mProfileImageContainer.setOnClickListener(this);
		mProfileImageContainer.setOnLongClickListener(this);
		mNameContainer.setOnClickListener(this);
		mNameContainer.setOnLongClickListener(this);
		mFollowMoreButton.setOnClickListener(this);
		mRetryButton.setOnClickListener(this);
		setListAdapter(null);
		mListView = getListView();
		mListView.addHeaderView(mHeaderView, null, false);
		mListView.setOnItemClickListener(this);
		mListView.setOnItemLongClickListener(this);
		setListAdapter(mAdapter);
		getUserListInfo(true, account_id, list_id, list_name, user_id, screen_name);

	}

	@Override
	public void onClick(final View view) {
		switch (view.getId()) {
			case R.id.follow_more: {
				if (mUserList == null) return;
				if (mAccountId != mUserId) {
					mFollowMoreButton.setVisibility(View.GONE);
					if (mUserList.isFollowing()) {
						mTwitterWrapper.destroyUserListSubscription(mAccountId, mUserList.getId());
					} else {
						mTwitterWrapper.createUserListSubscription(mAccountId, mUserList.getId());
					}
				} else {
					mPopupMenu = PopupMenu.getInstance(getActivity(), view);
					mPopupMenu.inflate(R.menu.action_user_list_details);
					final Menu menu = mPopupMenu.getMenu();
					final Intent extensions_intent = new Intent(INTENT_ACTION_EXTENSION_OPEN_USER_LIST);
					final Bundle extensions_extras = new Bundle();
					extensions_extras.putParcelable(INTENT_KEY_USER_LIST, new ParcelableUserList(mUserList, mAccountId,
							mHiResProfileImage));
					extensions_intent.putExtras(extensions_extras);
					addIntentToMenu(getActivity(), menu, extensions_intent);
					mPopupMenu.setOnMenuItemClickListener(this);
					mPopupMenu.show();
				}
				break;
			}
			case R.id.retry: {
				reloadUserListInfo();
				break;
			}
			case R.id.profile_image_container: {
				if (mAccountId > 0 && (mUserId > 0 || mUserScreenName != null)) {
					openUserProfile(getActivity(), new ParcelableUser(mUserList.getUser(), mAccountId,
							mHiResProfileImage));
				}
				break;
			}
		}

	}

	@Override
	public Loader<Response<UserList>> onCreateLoader(final int id, final Bundle args) {
		mListContainer.setVisibility(View.VISIBLE);
		mErrorMessageView.setText(null);
		mErrorMessageView.setVisibility(View.GONE);
		mErrorRetryContainer.setVisibility(View.GONE);
		setListShown(false);
		setProgressBarIndeterminateVisibility(true);
		long account_id = -1, user_id = -1;
		int list_id = -1;
		String screen_name = null, list_name = null;
		if (args != null) {
			account_id = args.getLong(INTENT_KEY_ACCOUNT_ID, -1);
			user_id = args.getLong(INTENT_KEY_USER_ID, -1);
			list_id = args.getInt(INTENT_KEY_LIST_ID, -1);
			list_name = args.getString(INTENT_KEY_LIST_NAME);
			screen_name = args.getString(INTENT_KEY_SCREEN_NAME);
		}
		return new ListInfoLoader(getActivity(), account_id, list_id, list_name, user_id, screen_name);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		mHeaderView = inflater.inflate(R.layout.user_list_detail_header, null);
		mProfileContainer = (ColorLabelRelativeLayout) mHeaderView.findViewById(R.id.profile_name_container);
		mNameContainer = mHeaderView.findViewById(R.id.name_container);
		mListNameView = (TextView) mHeaderView.findViewById(R.id.list_name);
		mUserNameView = (TextView) mHeaderView.findViewById(R.id.user_name);
		mDescriptionView = (TextView) mHeaderView.findViewById(R.id.description);
		mProfileImageView = (ImageView) mHeaderView.findViewById(R.id.profile_image);
		mProfileImageContainer = mHeaderView.findViewById(R.id.profile_image_container);
		mDescriptionContainer = mHeaderView.findViewById(R.id.description_container);
		mFollowMoreButton = (Button) mHeaderView.findViewById(R.id.follow_more);
		mListContainer = super.onCreateView(inflater, container, savedInstanceState);
		final View container_view = inflater.inflate(R.layout.list_with_error_message, null);
		((FrameLayout) container_view.findViewById(R.id.list_container)).addView(mListContainer);
		mErrorRetryContainer = container_view.findViewById(R.id.error_retry_container);
		mRetryButton = (Button) container_view.findViewById(R.id.retry);
		mErrorMessageView = (TextView) container_view.findViewById(R.id.error_message);
		return container_view;
	}

	@Override
	public void onDestroyView() {
		mUserList = null;
		getLoaderManager().destroyLoader(0);
		super.onDestroyView();
	}

	@Override
	public void onItemClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		final ListAction action = mAdapter.findItem(id);
		if (action != null) {
			action.onClick();
		}
	}

	@Override
	public boolean onItemLongClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		final ListAction action = mAdapter.findItem(id);
		if (action != null) return action.onLongClick();
		return false;
	}

	@Override
	public void onLoaderReset(final Loader<Response<UserList>> loader) {

	}

	@Override
	public void onLoadFinished(final Loader<Response<UserList>> loader, final Response<UserList> data) {
		if (data == null) return;
		if (getActivity() == null) return;
		if (data.value != null) {
			final UserList user = data.value;
			setListShown(true);
			changeUserList(mAccountId, user);
			mErrorRetryContainer.setVisibility(View.GONE);
		} else {
			if (data.exception != null) {
				mErrorMessageView.setText(data.exception.getMessage());
				mErrorMessageView.setVisibility(View.VISIBLE);
			}
			mListContainer.setVisibility(View.GONE);
			mErrorRetryContainer.setVisibility(View.VISIBLE);
		}
		setProgressBarIndeterminateVisibility(false);
	}

	@Override
	public boolean onLongClick(final View view) {
		if (mUserList == null) return false;
		final boolean is_my_activated_account = isMyActivatedAccount(getActivity(), mUserId);
		if (!is_my_activated_account) return false;
		switch (view.getId()) {
			case R.id.name_container:
			case R.id.description_container:
				final Bundle args = new Bundle();
				args.putLong(INTENT_KEY_ACCOUNT_ID, mAccountId);
				args.putString(INTENT_KEY_LIST_NAME, mUserList.getName());
				args.putString(INTENT_KEY_DESCRIPTION, mUserList.getDescription());
				args.putString(INTENT_KEY_TITLE, getString(R.string.description));
				args.putBoolean(INTENT_KEY_IS_PUBLIC, mUserList.isPublic());
				args.putInt(INTENT_KEY_LIST_ID, mUserList.getId());
				mEditUserListDialogFragment.setArguments(args);
				mEditUserListDialogFragment.show(getFragmentManager(), "edit_user_list_details");
				return true;
		}
		return false;
	}

	@Override
	public boolean onMenuItemClick(final MenuItem item) {
		switch (item.getItemId()) {
			case MENU_ADD: {
				final Bundle args = new Bundle();
				args.putLong(INTENT_KEY_ACCOUNT_ID, mAccountId);
				args.putString(INTENT_KEY_TEXT, "");
				args.putInt(INTENT_KEY_LIST_ID, mUserList.getId());
				mAddMemberDialogFragment.setArguments(args);
				mAddMemberDialogFragment.show(getFragmentManager(), "add_member");
				break;
			}
			case MENU_DELETE: {
				if (mUserId != mAccountId) return false;
				mTwitterWrapper.destroyUserList(mAccountId, mUserListId);
				break;
			}
			default: {
				if (item.getIntent() != null) {
					try {
						startActivity(item.getIntent());
					} catch (final ActivityNotFoundException e) {
						Log.w(LOGTAG, e);
						return false;
					}
				}
				break;
			}
		}
		return true;
	}

	@Override
	public void onStart() {
		// mDisplayName = mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_NAME,
		// true);
		super.onStart();
		final IntentFilter filter = new IntentFilter(BROADCAST_USER_LIST_DETAILS_UPDATED);
		filter.addAction(BROADCAST_USER_LIST_SUBSCRIPTION_CHANGED);
		registerReceiver(mStatusReceiver, filter);
	}

	@Override
	public void onStop() {
		unregisterReceiver(mStatusReceiver);
		super.onStop();
	}

	private void reloadUserListInfo() {
		getUserListInfo(false, mAccountId, mUserListId, mUserName, mUserId, mUserName);
	}

	public static class AddMemberDialogFragment extends BaseDialogFragment implements DialogInterface.OnClickListener {

		private AutoCompleteTextView mEditText;
		private String mText;
		private long mAccountId;
		private AsyncTwitterWrapper mTwitterWrapper;
		private int mListId;
		private AutoCompleteAdapter mUserAutoCompleteAdapter;

		@Override
		public void onClick(final DialogInterface dialog, final int which) {
			if (mListId <= 0 || mAccountId <= 0) return;
			switch (which) {
				case DialogInterface.BUTTON_POSITIVE: {
					mText = parseString(mEditText.getText());
					if (mText == null || mText.length() <= 0) return;
					mTwitterWrapper.addUserListMember(mAccountId, mListId, -1, mText);
					break;
				}
			}

		}

		@Override
		public Dialog onCreateDialog(final Bundle savedInstanceState) {
			mTwitterWrapper = getApplication().getTwitterWrapper();
			final Bundle bundle = savedInstanceState == null ? getArguments() : savedInstanceState;
			mAccountId = bundle != null ? bundle.getLong(INTENT_KEY_ACCOUNT_ID, -1) : -1;
			mListId = bundle != null ? bundle.getInt(INTENT_KEY_LIST_ID, -1) : -1;
			mText = bundle != null ? bundle.getString(INTENT_KEY_TEXT) : null;
			final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			final View view = LayoutInflater.from(getActivity()).inflate(R.layout.auto_complete_textview_default_style,
					null);
			builder.setView(view);
			mEditText = (AutoCompleteTextView) view.findViewById(R.id.edit_text);
			if (mText != null) {
				mEditText.setText(mText);
			}
			mUserAutoCompleteAdapter = new AutoCompleteAdapter(getActivity());
			mEditText.setAdapter(mUserAutoCompleteAdapter);
			mEditText.setThreshold(1);
			mEditText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(20) });
			builder.setTitle(R.string.screen_name);
			builder.setPositiveButton(android.R.string.ok, this);
			builder.setNegativeButton(android.R.string.cancel, this);
			return builder.create();
		}

		@Override
		public void onSaveInstanceState(final Bundle outState) {
			outState.putLong(INTENT_KEY_ACCOUNT_ID, mAccountId);
			outState.putInt(INTENT_KEY_LIST_ID, mListId);
			outState.putString(INTENT_KEY_TEXT, mText);
			super.onSaveInstanceState(outState);
		}

	}

	public static class EditUserListDialogFragment extends BaseDialogFragment implements
			DialogInterface.OnClickListener {

		private EditText mEditName, mEditDescription;
		private CheckBox mPublicCheckBox;
		private String mName, mDescription;
		private long mAccountId;
		private int mListId;
		private boolean mIsPublic;
		private AsyncTwitterWrapper mTwitterWrapper;

		@Override
		public void onClick(final DialogInterface dialog, final int which) {
			if (mAccountId <= 0) return;
			switch (which) {
				case DialogInterface.BUTTON_POSITIVE: {
					mName = parseString(mEditName.getText());
					mDescription = parseString(mEditDescription.getText());
					mIsPublic = mPublicCheckBox.isChecked();
					if (mName == null || mName.length() <= 0) return;
					mTwitterWrapper.updateUserListDetails(mAccountId, mListId, mIsPublic, mName, mDescription);
					break;
				}
			}

		}

		@Override
		public Dialog onCreateDialog(final Bundle savedInstanceState) {
			mTwitterWrapper = getApplication().getTwitterWrapper();
			final Bundle bundle = savedInstanceState == null ? getArguments() : savedInstanceState;
			mAccountId = bundle != null ? bundle.getLong(INTENT_KEY_ACCOUNT_ID, -1) : -1;
			mListId = bundle != null ? bundle.getInt(INTENT_KEY_LIST_ID, -1) : -1;
			mName = bundle != null ? bundle.getString(INTENT_KEY_LIST_NAME) : null;
			mDescription = bundle != null ? bundle.getString(INTENT_KEY_DESCRIPTION) : null;
			mIsPublic = bundle != null ? bundle.getBoolean(INTENT_KEY_IS_PUBLIC, true) : true;
			final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			final View view = LayoutInflater.from(getActivity()).inflate(R.layout.edit_user_list_detail, null);
			builder.setView(view);
			mEditName = (EditText) view.findViewById(R.id.name);
			mEditDescription = (EditText) view.findViewById(R.id.description);
			mPublicCheckBox = (CheckBox) view.findViewById(R.id.is_public);
			if (mName != null) {
				mEditName.setText(mName);
			}
			if (mDescription != null) {
				mEditDescription.setText(mDescription);
			}
			mPublicCheckBox.setChecked(mIsPublic);
			builder.setTitle(R.string.user_list);
			builder.setPositiveButton(android.R.string.ok, this);
			builder.setNegativeButton(android.R.string.cancel, this);
			return builder.create();
		}

		@Override
		public void onSaveInstanceState(final Bundle outState) {
			outState.putLong(INTENT_KEY_ACCOUNT_ID, mAccountId);
			outState.putInt(INTENT_KEY_LIST_ID, mListId);
			outState.putString(INTENT_KEY_LIST_NAME, mName);
			outState.putString(INTENT_KEY_DESCRIPTION, mDescription);
			outState.putBoolean(INTENT_KEY_IS_PUBLIC, mIsPublic);
			super.onSaveInstanceState(outState);
		}

	}

	public static class ListInfoLoader extends AsyncTaskLoader<Response<UserList>> {

		private final Twitter twitter;
		private final long user_id;
		private final int list_id;
		private final String screen_name, list_name;

		private ListInfoLoader(final Context context, final long account_id, final int list_id, final String list_name,
				final long user_id, final String screen_name) {
			super(context);
			this.user_id = user_id;
			this.list_id = list_id;
			this.screen_name = screen_name;
			this.list_name = list_name;
			twitter = getTwitterInstance(context, account_id, true);
		}

		@Override
		public Response<UserList> loadInBackground() {
			try {
				if (list_id > 0)
					return new Response<UserList>(twitter.showUserList(list_id), null);
				else
					return new Response<UserList>(findUserList(twitter, user_id, screen_name, list_name), null);
			} catch (final TwitterException e) {
				return new Response<UserList>(null, e);
			}
		}

		@Override
		public void onStartLoading() {
			forceLoad();
		}

	}

	class ListMembersAction extends ListAction {

		public ListMembersAction(final int order) {
			super(order);
		}

		@Override
		public String getName() {
			return getString(R.string.list_members);
		}

		@Override
		public String getSummary() {
			if (mUserList == null) return null;
			return String.valueOf(mUserList.getMemberCount());
		}

		@Override
		public void onClick() {
			openUserListMembers(getActivity(), mAccountId, mUserListId, mUserId, mUserScreenName, mListName);
		}

	}

	class ListSubscribersAction extends ListAction {

		public ListSubscribersAction(final int order) {
			super(order);
		}

		@Override
		public String getName() {
			return getString(R.string.list_subscribers);
		}

		@Override
		public String getSummary() {
			if (mUserList == null) return null;
			return String.valueOf(mUserList.getSubscriberCount());
		}

		@Override
		public void onClick() {
			openUserListSubscribers(getActivity(), mAccountId, mUserListId, mUserId, mUserScreenName, mListName);
		}

	}

	class ListTimelineAction extends ListAction {

		public ListTimelineAction(final int order) {
			super(order);
		}

		@Override
		public String getName() {
			return getString(R.string.list_timeline);
		}

		@Override
		public void onClick() {
			if (mUserList == null) return;
			openUserListTimeline(getActivity(), mAccountId, mUserListId, mUserId, mUserName, mListName);
		}

	}

	static class Response<T> {
		public final T value;
		public final TwitterException exception;

		public Response(final T value, final TwitterException exception) {
			this.value = value;
			this.exception = exception;
		}
	}

}
