// WARNING
//
// This file has been generated automatically by Visual Studio to store outlets and
// actions made in the UI designer. If it is removed, they will be lost.
// Manual changes to this file may not be handled correctly.
//
using Foundation;
using System.CodeDom.Compiler;

namespace Octothorpe.Mac
{
	[Register ("MainWindowController")]
	partial class MainWindowController
	{
		[Outlet]
		AppKit.NSPopUpButton ClassSelection { get; set; }

		[Outlet]
		AppKit.NSPopUpButton DeviceSelection { get; set; }

		[Outlet]
		AppKit.NSPopUpButton FileSelection { get; set; }

		[Outlet]
		AppKit.NSTextField MaxVersion { get; set; }

		[Outlet]
		AppKit.NSTextField MesuFileLoc { get; set; }

		[Outlet]
		AppKit.NSTextField MesuMinVersion { get; set; }

		[Outlet]
		AppKit.NSView MesuOptional { get; set; }

		[Outlet]
		AppKit.NSButton MesuRemoveStubs { get; set; }

		[Outlet]
		AppKit.NSTextField MesuURL { get; set; }

		[Outlet]
		AppKit.NSView MesuView { get; set; }

		[Outlet]
		AppKit.NSPopUpButton ModelSelection { get; set; }

		[Outlet]
		AppKit.NSBox NSBoxFile { get; set; }

		[Outlet]
		AppKit.NSBox NSBoxLoc { get; set; }

		[Outlet]
		AppKit.NSButton NSButtonParse { get; set; }

		[Outlet]
		AppKit.NSTextView NSTextViewOutput { get; set; }

		[Outlet]
		AppKit.NSTextField PallasCurrentBuild { get; set; }

		[Outlet]
		AppKit.NSTextField PallasCurrentVersion { get; set; }

		[Outlet]
		AppKit.NSView PallasOptional { get; set; }

		[Outlet]
		AppKit.NSTextField PallasRequestedVersion { get; set; }

		[Outlet]
		AppKit.NSButton PallasSupervised { get; set; }

		[Outlet]
		AppKit.NSView PallasView { get; set; }

		[Outlet]
		AppKit.NSButton ShowBeta { get; set; }

		[Outlet]
		AppKit.NSButton TableHeaders { get; set; }

		[Action ("BrowseForFile:")]
		partial void BrowseForFile (AppKit.NSButton sender);

		[Action ("ChangeOutputFormat:")]
		partial void ChangeOutputFormat (AppKit.NSButton sender);

		[Action ("ClassChanged:")]
		partial void ClassChanged (AppKit.NSPopUpButton sender);

		[Action ("DeviceChanged:")]
		partial void DeviceChanged (AppKit.NSPopUpButton sender);

		[Action ("ModelChanged:")]
		partial void ModelChanged (AppKit.NSPopUpButton sender);

		[Action ("ParsingSTART:")]
		partial void ParsingSTART (AppKit.NSButton sender);

		[Action ("SourceChanged:")]
		partial void PlistChanged (AppKit.NSPopUpButton sender);

		[Action ("SourceEdited:")]
		partial void PlistPathEdited (AppKit.NSTextField sender);
		
		void ReleaseDesignerOutlets ()
		{
			if (ClassSelection != null) {
				ClassSelection.Dispose ();
				ClassSelection = null;
			}

			if (DeviceSelection != null) {
				DeviceSelection.Dispose ();
				DeviceSelection = null;
			}

			if (FileSelection != null) {
				FileSelection.Dispose ();
				FileSelection = null;
			}

			if (MaxVersion != null) {
				MaxVersion.Dispose ();
				MaxVersion = null;
			}

			if (MesuFileLoc != null) {
				MesuFileLoc.Dispose ();
				MesuFileLoc = null;
			}

			if (MesuMinVersion != null) {
				MesuMinVersion.Dispose ();
				MesuMinVersion = null;
			}

			if (MesuOptional != null) {
				MesuOptional.Dispose ();
				MesuOptional = null;
			}

			if (MesuRemoveStubs != null) {
				MesuRemoveStubs.Dispose ();
				MesuRemoveStubs = null;
			}

			if (MesuURL != null) {
				MesuURL.Dispose ();
				MesuURL = null;
			}

			if (MesuView != null) {
				MesuView.Dispose ();
				MesuView = null;
			}

			if (ModelSelection != null) {
				ModelSelection.Dispose ();
				ModelSelection = null;
			}

			if (NSBoxFile != null) {
				NSBoxFile.Dispose ();
				NSBoxFile = null;
			}

			if (NSBoxLoc != null) {
				NSBoxLoc.Dispose ();
				NSBoxLoc = null;
			}

			if (NSButtonParse != null) {
				NSButtonParse.Dispose ();
				NSButtonParse = null;
			}

			if (NSTextViewOutput != null) {
				NSTextViewOutput.Dispose ();
				NSTextViewOutput = null;
			}

			if (PallasCurrentBuild != null) {
				PallasCurrentBuild.Dispose ();
				PallasCurrentBuild = null;
			}

			if (PallasCurrentVersion != null) {
				PallasCurrentVersion.Dispose ();
				PallasCurrentVersion = null;
			}

			if (PallasOptional != null) {
				PallasOptional.Dispose ();
				PallasOptional = null;
			}

			if (PallasRequestedVersion != null) {
				PallasRequestedVersion.Dispose ();
				PallasRequestedVersion = null;
			}

			if (PallasSupervised != null) {
				PallasSupervised.Dispose ();
				PallasSupervised = null;
			}

			if (PallasView != null) {
				PallasView.Dispose ();
				PallasView = null;
			}

			if (ShowBeta != null) {
				ShowBeta.Dispose ();
				ShowBeta = null;
			}

			if (TableHeaders != null) {
				TableHeaders.Dispose ();
				TableHeaders = null;
			}
		}
	}
}
