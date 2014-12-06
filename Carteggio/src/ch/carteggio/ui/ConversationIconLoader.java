/*   
 * Copyright (c) 2014, Lorenzo Keller
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This file contains code distributed with K-9 sources
 * that didn't include any copyright attribution header.
 *    
 */

package ch.carteggio.ui;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;

import android.app.ActionBar;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.util.LruCache;
import android.widget.ImageView;
import ch.carteggio.provider.CarteggioProviderHelper;

public class ConversationIconLoader {

    /**
     * Resize the pictures to the following value (device-independent pixels).
     */
    private static final int PICTURE_SIZE = 40;

    /**
     * Pattern to extract the letter to be displayed as fallback image.
     */
    private static final Pattern EXTRACT_LETTER_PATTERN = Pattern.compile("[a-zA-Z]");

    /**
     * Letter to use when {@link #EXTRACT_LETTER_PATTERN} couldn't find a match.
     */
    private static final String FALLBACK_CONTACT_LETTER = "?";

    private ContentResolver mContentResolver;
    private Resources mResources;
    private int mPictureSizeInPx;

    private CarteggioProviderHelper mHelper;
    
    private int mDefaultBackgroundColor;

    /**
     * LRU cache of contact pictures.
     */
    private final LruCache<Long, Bitmap> mBitmapCache;

    private final ArrayList<IconUpdate> mPendingIconUpdates = new ArrayList<ConversationIconLoader.IconUpdate>();
    
    /**
     * @see <a href="http://developer.android.com/design/style/color.html">Color palette used</a>
     */
    private final static int CONTACT_DUMMY_COLORS_ARGB[] = {
        0xff33B5E5,
        0xffAA66CC,
        0xff99CC00,
        0xffFFBB33,
        0xffFF4444,
        0xff0099CC,
        0xff9933CC,
        0xff669900,
        0xffFF8800,
        0xffCC0000
    };

    /**
     * Constructor.
     *
     * @param context
     *         A {@link Context} instance.
     * @param defaultBackgroundColor
     *         The ARGB value to be used as background color for the fallback picture. {@code 0} to
     *         use a dynamically calculated background color.
     */
    public ConversationIconLoader(Context context, int defaultBackgroundColor) {
        Context appContext = context.getApplicationContext();
        mContentResolver = appContext.getContentResolver();
        mResources = appContext.getResources();
        mHelper = new CarteggioProviderHelper(appContext);
        
        float scale = mResources.getDisplayMetrics().density;
        mPictureSizeInPx = (int) (PICTURE_SIZE * scale);

        mDefaultBackgroundColor = defaultBackgroundColor;

        ActivityManager activityManager =
                (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
        int memClass = activityManager.getMemoryClass();

        // Use 1/16th of the available memory for this memory cache.
        final int cacheSize = 1024 * 1024 * memClass / 16;

        mBitmapCache = new LruCache<Long, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(Long key, Bitmap bitmap) {
                // The cache size will be measured in bytes rather than number of items.
                return bitmap.getByteCount();
            }
        };
    }
    
    public void loadConversationPicture(long conversationId, ActionBar actionBar) {    	
		loadConversationPicture(conversationId, new ActionBarIconUpdate(actionBar));		
    }
    
    public void loadConversationPicture(long conversationId, ImageView imageView) {    	    	
		loadConversationPicture(conversationId, new ImageViewIconUpdate(imageView));		
    }
    
    private void loadConversationPicture(long conversationId, IconUpdate update) {
    	
    	boolean pending = false;
    	
    	for ( IconUpdate update2 : mPendingIconUpdates) {
    		if ( update2.hasSameTarget(update)) {
    			update = update2;
    			pending = true;
    			break;
    		}
    	}
    	
    	if ( pending == false ) {
    		mPendingIconUpdates.add(update);
    	}
    	
        Bitmap bitmap = getBitmapFromCache(conversationId);
        if (bitmap != null) {
            // The picture was found in the bitmap cache
            update.setBitmap(bitmap);
        } else if (cancelPotentialWork(conversationId, update)) {
            ConversationPictureRetrievalTask task = new ConversationPictureRetrievalTask(update, conversationId);
            
            update.setTask(task);            
            update.setBitmap(calculateFallbackBitmap(new String[] {"none"}));
                        
            try {
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } catch (RejectedExecutionException e) {
                // We flooded the thread pool queue... use a fallback picture
                update.setDrawable(new BitmapDrawable(update.getResources(), calculateFallbackBitmap(new String[] {"none"})));
            }
        }
    }

    private int calcUnknownContactColor(String email) {
        if (mDefaultBackgroundColor != 0) {
            return mDefaultBackgroundColor;
        }

        int val = email.hashCode();
        int rgb = CONTACT_DUMMY_COLORS_ARGB[Math.abs(val) % CONTACT_DUMMY_COLORS_ARGB.length];
        return rgb;
    }

    /**
     * Calculates a bitmap with a color and a capital letter for contacts without picture.
     */
    private Bitmap calculateFallbackBitmap(String emails[]) {
        Bitmap result = Bitmap.createBitmap(mPictureSizeInPx, mPictureSizeInPx,
                Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(result);

        int rgb = CONTACT_DUMMY_COLORS_ARGB[0];
        
        if ( emails.length > 0) {
            calcUnknownContactColor(emails[0]);        	
        }
        
        result.eraseColor(rgb);

        String letter = FALLBACK_CONTACT_LETTER;

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setARGB(255, 255, 255, 255);
        paint.setTextSize(mPictureSizeInPx * 3 / 4); // just scale this down a bit
        Rect rect = new Rect();
        paint.getTextBounds(letter, 0, 1, rect);
        float width = paint.measureText(letter);
        canvas.drawText(letter,
                (mPictureSizeInPx / 2f) - (width / 2f),
                (mPictureSizeInPx / 2f) + (rect.height() / 2f), paint);

        return result;
    }

    private void addBitmapToCache(long key, Bitmap bitmap) {
        if (getBitmapFromCache(key) == null) {
            mBitmapCache.put(key, bitmap);
        }
    }

    private Bitmap getBitmapFromCache(long key) {
        return mBitmapCache.get(key);
    }

    /**
     * Checks if a {@code ContactPictureRetrievalTask} was already created to load the contact
     * picture for the supplied {@code Address}.
     *
     * @param address
     *         The {@link Address} instance holding the email address that is used to search the
     *         contacts database.
     * @param holder
     *         The {@code QuickContactBadge} instance that will receive the picture.
     *
     * @return {@code true}, if the contact picture should be loaded in a background thread.
     *         {@code false}, if another {@link ContactPictureRetrievalTask} was already scheduled
     *         to load that contact picture.
     */
    private boolean cancelPotentialWork(long address, IconUpdate holder) {
        final ConversationPictureRetrievalTask task = holder.getTask();

        if (task != null) {
            if (address != task.getConversationId()) {
                // Cancel previous task
                task.cancel(true);
            } else {
                // The same work is already in progress
                return false;
            }
        }

        // No task associated with the ImageView, or an existing task was cancelled
        return true;
    }


    /**
     * Load a contact picture in a background thread.
     */
    class ConversationPictureRetrievalTask extends AsyncTask<Void, Void, Bitmap> {
        private final IconUpdate mIconUpdate;
        private final long mConversationId;

        ConversationPictureRetrievalTask(IconUpdate iconHolder, long conversationId) {
        	mIconUpdate = iconHolder;
            mConversationId = conversationId;
        }

        public long getConversationId() {
            return mConversationId;
        }

        @Override
        protected Bitmap doInBackground(Void... args) {
        	
        	String [] emails = mHelper.getParticipantsEmails(mConversationId);
        	
        	if ( emails.length == 0) {
        		return calculateFallbackBitmap(emails);	
        	}
        	
        	if ( emails.length > 1 ) {
        		return calculateFallbackBitmap(emails);
        	}
        	
            final String email = emails[0];
            final Uri photoUri = mHelper.getContactPhotoUri(email);
            Bitmap bitmap = null;
            if (photoUri != null) {
                try {
                    InputStream stream = mContentResolver.openInputStream(photoUri);
                    if (stream != null) {
                        try {
                            Bitmap tempBitmap = BitmapFactory.decodeStream(stream);
                            if (tempBitmap != null) {
                                bitmap = Bitmap.createScaledBitmap(tempBitmap, mPictureSizeInPx,
                                        mPictureSizeInPx, true);
                                if (tempBitmap != bitmap) {
                                    tempBitmap.recycle();
                                }
                            }
                        } finally {
                            try { stream.close(); } catch (IOException e) { /* ignore */ }
                        }
                    }
                } catch (FileNotFoundException e) {
                    /* ignore */
                }

            }

            if (bitmap == null) {
                bitmap = calculateFallbackBitmap(emails);
            }

            // Save the picture of the contact with that email address in the bitmap cache
            addBitmapToCache(mConversationId, bitmap);

            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
        	if (mIconUpdate.getTask() == this) {
                mIconUpdate.setBitmap(bitmap);
                mPendingIconUpdates.remove(mIconUpdate);
            }           
        }
    }
    
    private abstract class IconUpdate {
    	
    	private ConversationPictureRetrievalTask mTask;
    	
    	public void setTask(ConversationPictureRetrievalTask task) {
    		mTask = task;
    	}
    	
    	public ConversationPictureRetrievalTask getTask() {
    		return mTask;
    	}
    	
    	public void setBitmap(Bitmap bitmap) {
    		setDrawable(new BitmapDrawable(getResources(), bitmap));
    	}
    	    	
    	public abstract void setDrawable(Drawable d);
    	public abstract Resources getResources();
    	
    	public abstract boolean hasSameTarget(IconUpdate holder);
    }
    
    private class ActionBarIconUpdate extends IconUpdate {
			
		private WeakReference<ActionBar> mActionBar;		
		
		public ActionBarIconUpdate(ActionBar actionBar) {
			mActionBar = new WeakReference<ActionBar>(actionBar);
		}
		
		@Override
		public void setDrawable(Drawable d) {			
			ActionBar actionBar = mActionBar.get();
			
			if ( actionBar == null) return;
						
			actionBar.setIcon(d);
		}
		
		@Override
		public Resources getResources() {
			ActionBar actionBar = mActionBar.get();
			
			if ( actionBar == null) return null;
			
			return actionBar.getThemedContext().getResources();
		}
		
		public boolean hasSameTarget(IconUpdate holder) {
			
			if ( holder instanceof ActionBarIconUpdate ) {
				return ((ActionBarIconUpdate) holder).mActionBar.get() == mActionBar.get();
			}
			
			return false;
			
		}
		
    }

    private class ImageViewIconUpdate extends IconUpdate {
		
		private WeakReference<ImageView> mImageView;				
		
		public ImageViewIconUpdate(ImageView imageView) {
			mImageView = new WeakReference<ImageView>(imageView);			
		}
		
		@Override
		public void setDrawable(Drawable d) {								
			ImageView imageView = mImageView.get();
			
			if ( imageView == null) return;
			
			imageView.setImageDrawable(d);
		}
		
		@Override
		public Resources getResources() {
			ImageView imageView = mImageView.get();
			
			if ( imageView == null) return null;
			
			return imageView.getResources();
		}
		
		public boolean hasSameTarget(IconUpdate holder) {
			
			if ( holder instanceof ImageViewIconUpdate ) {
				return ((ImageViewIconUpdate) holder).mImageView.get() == mImageView.get();
			}
			
			return false;
			
		}
		
    }
	
}
