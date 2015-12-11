package de.westfalen.fuldix.aspectslider;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;

public class DirectorySelector extends Activity {

	public static final String START_DIR = "startDir";
	public static final String ONLY_DIRS = "onlyDirs";
	public static final String SHOW_HIDDEN = "showHidden";
	public static final String ALLOW_UP = "allowUp";
	public static final String RETURN_DIRECTORY = "chosenDir";
	public static final int SELECT_DIRECTORY = 11;
	private boolean showHidden = false;
	private boolean onlyDirs = true;
	private boolean allowUp = false;

	static private class Entry implements Comparable<Entry> {
		File file;
		int subdirs;
		int files;
		Entry() {
		}
		public Entry(File file) {
			this.file = file;
			file.listFiles(new FileFilter() {
				@Override
				public boolean accept(File pathname) {
                    if(pathname.isDirectory()) {
                        subdirs++;
                    }
                    if(pathname.isFile()) {
                        files++;
                    }
					return false;
				}
			});
		}
		@Override
		public int compareTo(Entry other) {
			return file.getName().compareToIgnoreCase(other.file.getName());
		}
	}
	static private class UpEntry extends Entry {
		public UpEntry(File file) {
			this.file = file;
		}
	}

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle extras = getIntent().getExtras();
        File dir = Environment.getExternalStorageDirectory();
        if (extras != null) {
        	String preferredStartDir = extras.getString(START_DIR);
        	showHidden = extras.getBoolean(SHOW_HIDDEN, false);
        	onlyDirs = extras.getBoolean(ONLY_DIRS, true);
        	allowUp = extras.getBoolean(ALLOW_UP, true);
        	if(preferredStartDir != null) {
            	File startDir = new File(preferredStartDir);
            	if(startDir.isDirectory()) {
            		dir = startDir;
            	}
            } 
        }

        setContentView(R.layout.activity_directoryselect);
        setTitle(dir.getAbsolutePath());

        final View.OnClickListener listButtonListener = new View.OnClickListener() {
            public void onClick(View v) {
                returnDir((String) v.getTag());
            }
        };

        Button thisdirbutton = (Button) findViewById(R.id.thisdirbutton);
        String thisname = dir.getName();
        if(dir.getParentFile() == null) {
            thisname = getString(R.string.dir_root);
        }
        thisdirbutton.setText(thisname);
        thisdirbutton.setTag(dir.getAbsolutePath());
        thisdirbutton.setOnClickListener(listButtonListener);

        GridView gv = (GridView) findViewById(R.id.dirgrid);
        gv.setTextFilterEnabled(true);

        Point realSize = new Point();
        if (Build.VERSION.SDK_INT >= 17) {
            getRealSize(realSize);
        } else {
            realSize.x = getWindowManager().getDefaultDisplay().getWidth();
            realSize.y = getWindowManager().getDefaultDisplay().getHeight();
        }
        int cellWidth = getResources().getDimensionPixelSize(R.dimen.dir_column_width_intended);
        int numColumns = realSize.x/cellWidth;
        gv.setNumColumns(numColumns);

        if(!dir.canRead()) {
        	Context context = getApplicationContext();
        	Toast toast = Toast.makeText(context, R.string.dir_cannotread, Toast.LENGTH_LONG);
        	toast.show();
        	return;
        }
 
        final ArrayList<Entry> entries = new ArrayList<>();
        dir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				if(onlyDirs && !file.isDirectory())
					return false;
				if(!showHidden && file.isHidden())
					return false;
				entries.add(new Entry(file));
				return false;
			}
        });
        if (entries.size() > 0) {
        	Collections.sort(entries);
        }
		if(allowUp) {
			File parent = dir.getParentFile();
			if(parent != null) {
				entries.add(0, new UpEntry(parent));
			}
		}
        gv.setAdapter(new ArrayAdapter<Entry>(this, R.layout.dirlist_item, entries) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = convertView;
                if (view == null) {
                    LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = inflater.inflate(R.layout.dirlist_item, null);
                }

                Entry item = getItem(position);
                TextView dirnameView = (TextView) view.findViewById(R.id.dirname);
                TextView dirinfoView = (TextView) view.findViewById(R.id.dirinfo);
                View dirbutton = view.findViewById(R.id.dirbutton);
                if (item != null) {
                    if (item instanceof UpEntry) {
                        dirnameView.setText(R.string.dir_up);
                        dirnameView.setVisibility(View.VISIBLE);
                        String parentname = item.file.getName();
                        if (parentname.equals("")) {
                            parentname = getString(R.string.dir_root);
                        }
                        dirinfoView.setText(String.format(getString(R.string.dir_upto_format), parentname));
                        dirinfoView.setVisibility(View.VISIBLE);
                        dirbutton.setVisibility(View.GONE);
                        view.setEnabled(true);
                    } else {
                        dirnameView.setText(item.file.getName());
                        dirnameView.setVisibility(View.VISIBLE);
                        boolean isempty = false;
                        Resources res = getResources();
                        String fileStr = null;
                        if(item.files > 0) {
                            fileStr = res.getQuantityString(R.plurals.dir_info_format_files, item.files, item.files);
                        }
                        String subdirStr = null;
                        if (item.subdirs > 0) {
                            subdirStr = res.getQuantityString(R.plurals.dir_info_format_subdirs, item.subdirs, item.subdirs);
                        }
                        if (fileStr != null) {
                            if (subdirStr != null) {
                                dirinfoView.setText(String.format(getString(R.string.dir_info_format_files_and_subdirs), fileStr, subdirStr));
                            } else {
                                dirinfoView.setText(fileStr);
                            }
                        } else {
                            if (subdirStr != null) {
                                dirinfoView.setText(subdirStr);
                            } else {
                                dirinfoView.setText(getString(R.string.dir_info_format_empty));
                                isempty = true;
                            }
                        }
                        if (isempty) {
                            dirbutton.setVisibility(View.GONE);
                            view.setEnabled(false);
                        } else {
                            dirbutton.setVisibility(View.VISIBLE);
                            dirbutton.setTag(item.file.getAbsolutePath());
                            dirbutton.setOnClickListener(listButtonListener);
                            view.setEnabled(true);
                        }
                        dirinfoView.setVisibility(View.VISIBLE);
                    }
                }
                return view;
            }
        });

        gv.setOnItemClickListener(new OnItemClickListener() {
        	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        		Entry entry = entries.get(position);
        		if(!entry.file.isDirectory())
        			return;
        		if(!(entry instanceof UpEntry) && entry.subdirs == 0 && entry.files == 0)
        			return;
        		String path = entry.file.getAbsolutePath();
                Intent intent = new Intent(DirectorySelector.this, DirectorySelector.class);
                intent.putExtra(DirectorySelector.START_DIR, path);
                intent.putExtra(DirectorySelector.SHOW_HIDDEN, showHidden);
                intent.putExtra(DirectorySelector.ONLY_DIRS, onlyDirs);
                intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                startActivity(intent);
        	}
        });
    }

    private void returnDir(String path) {
    	Intent result = new Intent();
    	result.putExtra(RETURN_DIRECTORY, path);
        setResult(RESULT_OK, result);
    	finish();
    }

    @TargetApi(17)
    private void getRealSize(Point realSize) {
        getWindowManager().getDefaultDisplay().getRealSize(realSize);
    }
}
