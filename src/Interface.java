/*
 * OTA Catalog Parser
 * Copyright (c) 2016 Dialexio
 * 
 * The MIT License (MIT)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
import org.eclipse.swt.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

public class Interface {
	private static final String VERSION = "1.0";

	private static boolean file = false;
	private static Display display;
	private static final Parser parser = new Parser();
	private static Text deviceText, maxText, minText, modelText, output;

	private static void browseForFile(Shell shell) {
		final FileDialog filePrompt = new FileDialog(shell, SWT.OPEN);
		MessageBox error = new MessageBox(shell, SWT.OK);

		error.setText("Error");

		filePrompt.setFilterNames(new String[] {"XML file (.xml)", "Apple Property List (.plist)"});
		filePrompt.setFilterExtensions(new String[] {"*.xml", "*.plist"});
		filePrompt.setText("Locate the OTA catalog you wish to parse.");
		filePrompt.open();

		if (filePrompt.getFileName().isEmpty()) {
			output.setText("You need to select an OTA catalog to proceed.");
			file = false;
		}

		else {
			switch (parser.loadFile(filePrompt.getFilterPath() + '/' + filePrompt.getFileName())) {
				case 0:
					output.setText('"' + filePrompt.getFileName() + "\" selected!");
					file = true;
					break;

				case 2:
					error.setMessage("Couldn't find that file.");
					error.open();
					file = false;
					break;

				case 6:
					error.setMessage("This isn't an Apple property list.");
					error.open();
					file = false;
					break;

				case 7:
					error.setMessage("This is an Apple property list, but it's not one of Apple's OTA update catalogs.");
					error.open();
					file = false;
					break;

				default:
					error.setMessage("You done messed up now.");
					error.open();
					file = false;
					break;
			}
		}
	}

	/**
	 * @wbp.parser.entryPoint
	 */
	private static void displayWindow() {
		Button wikiRadio;
		final Button betaButton, fileButton, parseButton;
		final Composite deviceField, maxField, minField, modelField, widgets;
		final Group optional;
		final Label deviceLabel, minLabel, maxLabel, modelLabel;

		final Shell shell = new Shell(display, SWT.CLOSE | SWT.MIN | SWT.TITLE);
		shell.setText("OTA Catalog Parser v" + VERSION);
		shell.setLayout(new GridLayout(2, false));

		widgets = new Composite(shell, SWT.NONE);

			fileButton = new Button(widgets, SWT.NONE);
			fileButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
			fileButton.setText("Browse for File");

			deviceField = new Composite(widgets, SWT.NONE);
			deviceField.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
			GridLayout gl_deviceField = new GridLayout(2, false);
			gl_deviceField.marginHeight = 0;
			deviceField.setLayout(gl_deviceField);
				deviceLabel = new Label(deviceField, SWT.NONE);
				deviceLabel.setText("Device:");
				deviceLabel.setToolTipText("Enter a device type, such as iPhone5,1.");
				deviceText = new Text(deviceField, SWT.BORDER);
				GridData gd_deviceTextField = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
				gd_deviceTextField.widthHint = 110;
				deviceText.setLayoutData(gd_deviceTextField);
				deviceText.setToolTipText("Enter a device type, such as iPhone5,1.");

			modelField = new Composite(widgets, SWT.NONE);
			modelField.setLayout(new GridLayout(2, false));
			modelField.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
				modelLabel = new Label(modelField, SWT.NONE);
				modelLabel.setText ("Model:");
				modelLabel.setToolTipText("This field is required for the iPhone 6S or 6S Plus.\nYou need to enter a value like \"N71AP.\"");
				modelText = new Text(modelField, SWT.BORDER);
				GridData gd_modelTextField = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
				gd_modelTextField.widthHint = 70;
				modelText.setLayoutData(gd_modelTextField);
				modelText.setToolTipText("This field is required for the iPhone 6S or 6S Plus.\nYou need to enter a value like \"N71AP.\"");
			modelField.setVisible(false);

			wikiRadio = new Button(widgets, SWT.RADIO);
			wikiRadio.setText("iPhone Wiki output");
			wikiRadio.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					parser.wikiMarkup(true);
				}
			});
			wikiRadio = new Button(widgets, SWT.RADIO);
			wikiRadio.setText("Human-readable output");
			wikiRadio.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					parser.wikiMarkup(false);
				}
			});
			wikiRadio.setSelection(true);

			optional = new Group(widgets, SWT.NONE);
			optional.setText("Optional");
			optional.setLayout(new GridLayout(1, false));

				betaButton = new Button(optional, SWT.CHECK);
				betaButton.setText("Search for betas");
				betaButton.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						parser.showBeta(betaButton.getSelection());
					}
				});

				minField = new Composite(optional, SWT.NONE);
				minField.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
				GridLayout gl_minField = new GridLayout(2, false);
				gl_minField.marginHeight = 1;
				minField.setLayout(gl_minField);
				minLabel = new Label(minField, SWT.NONE);
				minLabel.setText("Minimum version:");
				minText = new Text(minField, SWT.BORDER);

				maxField = new Composite(optional, SWT.NONE);
				maxField.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
				maxField.setLayout(new GridLayout(2, false));
				maxLabel = new Label(maxField, SWT.NONE);
				maxLabel.setText("Maximum version:");
				maxText = new Text(maxField, SWT.BORDER);

			parseButton = new Button(widgets, SWT.PUSH);
			parseButton.setEnabled(false);
			parseButton.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
			parseButton.setText("Parse");
			parseButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					output.setText("");
					parser.setDevice(deviceText.getText());

					if (maxText.getText() != null)
						parser.setMax(maxText.getText());

					if (minText.getText() != null)
						parser.setMin(minText.getText());

					if (modelText.getText() != null)
						parser.setModel(modelText.getText());

					parser.parse();
				}
			});

			deviceText.addModifyListener(new ModifyListener() {
				@Override
				public void modifyText(ModifyEvent e) {
					// Show the model field only if we're looking for 6S or 6S Plus.
					modelField.setVisible(deviceText.getText().matches("iPhone8,(1|2)"));

					// Set the parse button's enable status after a device is entered.
					parseButton.setEnabled(parseButtonStatus());
				}
			});
			// Set the parse button's enable status after a file is (not) selected.
			fileButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					browseForFile(shell);
					parseButton.setEnabled(parseButtonStatus());
				}
			});

		widgets.setLayout(new GridLayout());

		output = new Text(shell, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI);
		output.setEditable(false);
		GridData gd_output = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_output.heightHint = 320;
		gd_output.widthHint = 450;
		output.setLayoutData(gd_output);
		parser.defineOutput(output);

		shell.setDefaultButton(parseButton);
		shell.pack();
		shell.open();

		while (!shell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
	}

	public static void main(String[] args) {
		// No arguments? Launch the GUI.
		if (args.length == 0) {
			display = new Display();
			display.setAppName("OTA Catalog Parser");
			display.setAppVersion(VERSION);

			displayWindow();

			display.dispose();
		}

		else {
			parser.defineOutput(null);
			int i = 0;

			// Reading arguments (and performing some basic checks).
			while (i < args.length && args[i].charAt(0) == '-') {
				switch (args[i++]) {
					case "-b":
						parser.showBeta(true);
						break;

					case "-d":
						if (i < args.length)
							parser.setDevice(args[i++]);
						break;

					case "-f":
						if (i < args.length) {
							int errorCode = parser.loadFile(args[i++]);
							if (errorCode != 0)
								System.exit(errorCode);
						}
						break;

					case "-h":
						System.out.println("OTA Catalog Parser v" + VERSION);
						System.out.println("https://github.com/Dialexio/OTA-Catalog-Parser-Java");

						System.out.println("\nRequired Arguments:");
						System.out.println("-d <device>      Choose the device you are searching for. (e.g. iPhone8,1)");
						System.out.println("-f <file>        Specify the path to the XML file you are searching in.");
						System.out.println("-m <model>       Choose the model you are searching for. (e.g. N71mAP)\n                 This is only used and required for iPhone 6S or 6S Plus.");

						System.out.println("\nOptional Arguments:");
						System.out.println("-b               Displays beta firmwares. By default, this is disabled.");
						System.out.println("-max <version>   Choose the highest firmware version you are searching for. (e.g. 9.0.2)");
						System.out.println("-min <version>   Choose the lowest firmware version you are searching for. (e.g. 8.4.1)");
						System.out.println("-w               Formats the output for The iPhone Wiki.");
						System.exit(0);
						break;

					case "-m":
						if (i < args.length)
							parser.setModel(args[i++]);
						break;

					case "-max":
						if (i < args.length)
							parser.setMax(args[i++]);
						break;

					case "-min":
						if (i < args.length)
							parser.setMin(args[i++]);
						break;

					case "-w":
						parser.wikiMarkup(true);
						break;
				}
			}

			parser.parse();
		}
	}

	private static boolean parseButtonStatus() {
		return file && deviceText.getText().matches("(AppleTV|iPad|iPhone|iPod)(\\d)?\\d,\\d");
	}
}