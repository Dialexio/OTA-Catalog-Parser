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
	private static final String VERSION = "1.1.2";

	private static Display display;
	private static final Parser parser = new Parser();
	private static MessageBox error;
	private static Shell shell;
	private static Text deviceText, maxText, minText, modelText, output;

	/**
	 * @wbp.parser.entryPoint
	 */
	private static void displayWindow() {
		shell = new Shell(display, SWT.CLOSE | SWT.MIN | SWT.TITLE);
		error = new MessageBox(shell, SWT.OK);

		Button wikiRadio;
		final Button betaButton, parseButton;
		final Combo xmlDropdown;
		final Composite deviceField, minMaxFields, modelField, urlField, widgets, xmlFields;
		final Group optional;
		final Label deviceLabel, minLabel, maxLabel, modelLabel, urlLabel, xmlSelectionLabel;
		final Text urlText;

		shell.setText("OTA Catalog Parser v" + VERSION);
		shell.setLayout(new GridLayout(2, false));

		widgets = new Composite(shell, SWT.NONE);

		xmlFields = new Composite(widgets, SWT.NONE);
		xmlFields.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		GridLayout gl_xmlFields = new GridLayout(1, false);
		xmlFields.setLayout(gl_xmlFields);

		xmlSelectionLabel = new Label(xmlFields, SWT.NONE);
		xmlSelectionLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
		xmlSelectionLabel.setText("Select a catalog to load:");
		xmlDropdown = new Combo(xmlFields, SWT.READ_ONLY);
		xmlDropdown.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		xmlDropdown.setItems(new String[]{"iOS (Public)", "tvOS (Public)", "watchOS (Public)", "Custom URL…", "Load File…"});

		urlField = new Composite(xmlFields, SWT.NONE);
		GridLayout gl_urlField = new GridLayout(2, false);
		gl_urlField.marginHeight = 0;
		urlField.setLayout(gl_urlField);
		urlField.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		urlField.setVisible(false);
		urlLabel = new Label(urlField, SWT.NONE);
			urlLabel.setText ("URL:");
			urlLabel.setToolTipText("Enter a mesu.apple.com URL that points to a list of OTA updates.");
			urlText = new Text(urlField, SWT.BORDER);
			urlText.setText("http://mesu.apple.com/assets/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml");
			GridData gd_urlText = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
			gd_urlText.widthHint = 150;
			urlText.setLayoutData(gd_urlText);
			urlText.setToolTipText("Enter a mesu.apple.com URL that points to a list of OTA updates.");

		Composite deviceFields = new Composite(widgets, SWT.NONE);
		deviceFields.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
		deviceFields.setLayout(new GridLayout(1, false));

			deviceField = new Composite(deviceFields, SWT.NONE);
			GridLayout gl_deviceField = new GridLayout(2, false);
			gl_deviceField.marginHeight = 0;
			deviceField.setLayout(gl_deviceField);
				deviceLabel = new Label(deviceField, SWT.NONE);
				deviceLabel.setText("Device:");
				deviceLabel.setToolTipText("Enter a device type, such as iPhone5,1.");
				deviceText = new Text(deviceField, SWT.BORDER);
				GridData gd_deviceTextField = new GridData(SWT.LEFT, SWT.CENTER, false, false);
				gd_deviceTextField.widthHint = 110;
				deviceText.setLayoutData(gd_deviceTextField);
				deviceText.setToolTipText("Enter a device type, such as iPhone5,1.");

			modelField = new Composite(deviceFields, SWT.NONE);
			modelField.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
			GridLayout gl_modelField = new GridLayout(2, false);
			gl_modelField.marginHeight = 0;
			modelField.setLayout(gl_modelField);
				modelLabel = new Label(modelField, SWT.NONE);
				modelLabel.setText ("Model:");
				modelLabel.setToolTipText("This field is required for the iPhone 6S or 6S Plus.\nYou need to enter a value like \"N71AP.\"");
				modelText = new Text(modelField, SWT.BORDER);
				GridData gd_modelTextField = new GridData(SWT.LEFT, SWT.CENTER, false, false);
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
				betaButton.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
				betaButton.setText("Search for betas");
				betaButton.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						parser.showBeta(betaButton.getSelection());
					}
				});

				minMaxFields = new Composite(optional, SWT.NONE);
				minMaxFields.setLayout(new GridLayout(2, false));
					minLabel = new Label(minMaxFields, SWT.NONE);
					minLabel.setText("Minimum version:");
					minText = new Text(minMaxFields, SWT.BORDER);

					maxLabel = new Label(minMaxFields, SWT.NONE);
					maxLabel.setText("Maximum version:");
					maxText = new Text(minMaxFields, SWT.BORDER);

			parseButton = new Button(widgets, SWT.PUSH);
			parseButton.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
			parseButton.setText("Parse");
			parseButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					try {
						if (deviceText.getText().matches("(AppleTV|iPad|iPhone|iPod|Watch)(\\d)?\\d,\\d") == false)
							throw new Exception("You did not provide a device to search for.");

						parser.setDevice(deviceText.getText());

						if (maxText.getText() != null)
							parser.setMax(maxText.getText());

						if (minText.getText() != null)
							parser.setMin(minText.getText());

						if (xmlDropdown.getSelectionIndex() < 0)
							throw new Exception("No property list selected.");

						if (parser.setModel(modelText.getText()) == false)
							throw new Exception("To find OTA updates for " + deviceText.getText() + ", you must specify a model number. For example, N71AP is a model number for the iPhone 6S.");

						switch (xmlDropdown.getItem(xmlDropdown.getSelectionIndex())) {
							case "Custom URL…":
								switch (parser.loadXML(urlText.getText())) {
									case 9:
										throw new Exception("The URL supplied should belong to mesu.apple.com.");

									case 10:
										throw new Exception("mesu.apple.com cannot be resolved.");
								}
								output.setText("");
								break;

							case "iOS (Public)":
								if (parser.loadXML("http://mesu.apple.com/assets/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml") == 10)
									throw new Exception("mesu.apple.com cannot be resolved.");
								output.setText("");
								break;

							case "tvOS (Public)":
								if (parser.loadXML("http://mesu.apple.com/assets/tv/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml") == 10)
									throw new Exception("mesu.apple.com cannot be resolved.");
								output.setText("");
								break;

							case "watchOS (Public)":
								if (parser.loadXML("http://mesu.apple.com/assets/watch/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml") == 10)
									throw new Exception("mesu.apple.com cannot be resolved.");
								output.setText("");
								break;

							default:
								final FileDialog filePrompt = new FileDialog(shell, SWT.OPEN);

								filePrompt.setFilterNames(new String[] {"XML file (.xml)", "Apple Property List (.plist)"});
								filePrompt.setFilterExtensions(new String[] {"*.xml", "*.plist"});
								filePrompt.setText("Locate the OTA catalog you wish to parse.");
								filePrompt.open();

								if (filePrompt.getFileName().isEmpty()) {
									throw new Exception("You need to select an OTA catalog to proceed.");
								}

								else {
									switch (parser.loadXML(filePrompt.getFilterPath() + '/' + filePrompt.getFileName())) {
										case 0:
											output.setText("");
											break;

										case 2:
											throw new Exception("The file can't be found.");

										case 6:
											throw new Exception("This isn't an Apple property list.");

										case 7:
											throw new Exception("This is an Apple property list, but it's not one of Apple's OTA update catalogs.");

										default:
											throw new Exception("I'm not sure how, but you screwed up big time.");
									}
								}
								break;
						}

						parser.parse();
					}

					catch (Exception objection) {
						error.setMessage(objection.getMessage());
						error.open();
					}
				}
			});

			deviceText.addModifyListener(new ModifyListener() {
				@Override
				public void modifyText(ModifyEvent e) {
					// Show the model field only if we're looking for 6S or 6S Plus.
					modelField.setVisible(deviceText.getText().matches("iPhone8,(1|2|4)"));
				}
			});

			xmlDropdown.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					urlField.setVisible(xmlDropdown.getItem(xmlDropdown.getSelectionIndex()).equals("Custom URL…"));
				}
			});

		widgets.setLayout(new GridLayout());

		output = new Text(shell, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI);
		GridData gd_output = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_output.heightHint = 400;
		gd_output.widthHint = 600;
		output.setLayoutData(gd_output);
		output.setEditable(false);
		parser.defineOutput(output);

		shell.setDefaultButton(parseButton);
		shell.pack();
		shell.open();

		while (shell.isDisposed() == false) {
			if (display.readAndDispatch() == false)
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
							int errorCode = parser.loadXML(args[i++]);
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
}