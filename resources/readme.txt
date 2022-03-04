Thank you for downloading Dayon!

Contents:

  .            Startup scripts, configuration files and the dayon.jar
  license/     Dayon! & bundled software licenses.
  jre/         Bundled JRE (optional).
  
  
Preferences (e.g., window location) & log file are saved in the directory:

  USER_HOME/.dayon
  
  
Running Dayon!

  On the assistant machine (acting as a server) click the Dayon.Assistant link or run the dayon_assistant script.
  Open the connection port (e.g., firewall, DSL router, NAT, etc...) and generat an access token by clicking the key symbol.

  The settings (e.g., number of capture per second, compression method, ...) will be sent
  to the assisted machine during the connection. The assisted has nothing to do except entering the access token or
  the IP address and port number as described below.

  On the assisted machine (acting as a client) click the Dayon! link or run the dayon_assisted script.
  Enter access token or the (external) IP address and port number of the assistant machine.


Linux:

  If you want to install from the tar archive, then you may need to chmod +x setup.sh before executing it.


OSX/macOS:

  You might need to chmod +x the scripts dayon, dayon_assistant and dayon_assisted.
  You will also have to grant the "Screen Recording" permission to dayon_assisted:
  System Preferences => Security and Privacy => Privacy => Screen Recording


Project:

  The project is available at the following URLs:

	https://retgal.github.io/Dayon
	https://github.com/retgal/dayon
	https://sourceforge.net/projects/dayonactive
	https://snapcraft.io/dayon
	https://code.launchpad.net/~regal/+archive/ubuntu/dayon

Enjoy!