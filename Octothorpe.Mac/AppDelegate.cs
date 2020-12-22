using AppKit;
using Foundation;

namespace Octothorpe.Mac
{
	[Register("AppDelegate")]
	public partial class AppDelegate : NSApplicationDelegate
	{
		MainWindowController mainWindowController;

		public AppDelegate()
		{
		}

		public override void DidFinishLaunching(NSNotification notification)
		{
			mainWindowController = new MainWindowController();
			mainWindowController.Window.MakeKeyAndOrderFront(this);
		}

		public override void WillTerminate(NSNotification notification)
		{
			// Insert code here to tear down your application
		}

        #region Custom actions
		partial void UpdateSourceChanged(NSMenuItem sender)
        {
			mainWindowController.ParserModeChanged(sender.Title);
		}

		#endregion
	}
}
