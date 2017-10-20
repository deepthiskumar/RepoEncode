import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;




public class CCEncodeFull {
	private final static Logger log = Logger.getLogger(CCEncodeFull.class.getName());
	private static HashMap<String,String> renameList = null;

	public static void main(String[] args) throws SecurityException, IOException
	{
		if(args.length != 5 )
			System.out.println("Usage: <Repos directory> <branch> <CCTrack location> <no of repos> <renamesfile directory>");
		String reposFolder = args[0];
		String branchName = args[1];
		String ccTrack = args[2];
		int noOfRepos = Integer.parseInt(args[4]);
		int startRepo = Integer.parseInt(args[3]);
		String rfolder = args[4];
		
		SimpleDateFormat format = new SimpleDateFormat("MMddyyyyHHmmss");
		FileHandler fh = new FileHandler(reposFolder+"/Logs/CCEncodeFull"+format.format(Calendar.getInstance().getTime())+".log");  
        log.addHandler(fh);
        fh.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + "\n";
            }
        });
        List<Integer> failedrepos = Arrays.asList(0);
		for(int i = startRepo ; i<= noOfRepos ; i ++ )
		{
			renameList = new HashMap<String,String>();
			if(failedrepos.contains(i))
			{
				log.info("Skipping repo"+i);
				continue;
			}
			log.info("Reading repo"+i+".renames file");
			readRenamesFile(i,rfolder);
			log.info("Reading completed");
			long time = System.currentTimeMillis();
			String repo = reposFolder+"/repo"+i;
			encodeAllCommits(repo,reposFolder,branchName,ccTrack);
			long endTime = System.currentTimeMillis();
			log.info("Encoding repo"+i+" completed in "+ (endTime - time) + " ms");
		}
	}

	private static void readRenamesFile(int i, String rfolder) {
		HashMap<String,ArrayList<String>> renameListTemp = new HashMap<String,ArrayList<String>>();
		File rfile = new File(rfolder+"/repo"+i+".renames");
		FileReader fin;
		try {
			fin = new FileReader(rfile);
			BufferedReader buf = new BufferedReader(fin);
			String line = null;
			while((line = buf.readLine()) != null)
			{
				String[] str = line.split(":");
				if(str.length != 2)
					continue;
				String newFile = str[0];
				String oldFile = str[1];
				boolean found = false;
				for(Entry<String, ArrayList<String>> a : renameListTemp.entrySet())
				{
					if(a.getValue().contains(oldFile))
					{
						ArrayList<String> list = a.getValue();
						list.add(newFile);
						renameListTemp.put(a.getKey(),list);
						found = true;
						break;
					}
				}
				if(!found)
				{
					ArrayList<String> list = new ArrayList<String>();
					list.add(newFile);
					renameListTemp.put(oldFile, list);
				}
				
			}
			for(Entry<String, ArrayList<String>> a : renameListTemp.entrySet())
			{
				log.info("No of renames for file "+a.getKey()+ " : "+a.getValue().size());
				for(String newFile : a.getValue())
				{
					log.info(newFile);
					renameList.put(newFile, a.getKey());
				}
			}
		} catch (FileNotFoundException e) {
			log.info("Could not find file "+rfolder+"/repo"+i+".renames");
			return;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
	}

	private static void encodeAllCommits(String repo, String reposFolder, String branchName, String ccTrack) {
		ArrayList<String> commits = getAllCommits(repo,reposFolder,branchName, ccTrack);
		
	}

	private static ArrayList<String> getAllCommits(String repo, String reposFolder, String branchName, String ccTrack) {
		String commitHash;
		int totalCommits = 0;
		boolean isRoot = false;
		try
		{
			File dir = new File(repo);
			//checkout to the specified branch
			checkoutBranch(dir, branchName);
			log.info("Getting no-merge log for Repo: "+repo+ " ("+dir.getName()+")");
			//get all the commits in chronological order
			ProcessBuilder pb = new ProcessBuilder("git", "log", "--pretty=format:'%H'","--reverse");//,"--no-merges");
			pb.directory(dir);
			Process p = pb.start();
			BufferedReader bufR = new BufferedReader(new InputStreamReader(p.getInputStream()));
	        BufferedReader bufR1;
	        while((commitHash = bufR.readLine()) != null && !commitHash.equals(""))
	        {
	        	if(totalCommits == 0)
	        		isRoot = true;
	        	totalCommits ++;
	        	commitHash = commitHash.trim();
	        	commitHash = commitHash.substring(1, commitHash.length()-1);
	        	log.info("Encoding files in commmit "+ commitHash + " (Dimension "+totalCommits+")");
	        	//checkout to the commit
	        	checkout(commitHash,dir);
	        	//get all the commit files [TODO Check if renames files are appearing properly]
	        	ArrayList<String> changedfiles = getCommitFiles(commitHash,isRoot,repo);
	        	encode(changedfiles,ccTrack,repo,totalCommits,branchName);
	        	isRoot = false;
	        }
		}
		catch(Exception e)
		{
			log.info("Error: "+e.getStackTrace());
		}
		return null;
	}

	private static void checkoutBranch(File dir, String branchName) throws InterruptedException, IOException {
		ProcessBuilder pb = new ProcessBuilder("git", "checkout", "-f", branchName);
		pb.directory(dir);
		Process p = pb.start();
		p.waitFor();
		
	}

	private static void encode(ArrayList<String> changedfiles, String ccTrack, String repo, int totalCommits, String branchName) throws IOException, InterruptedException {
		ProcessBuilder pb ;
		Process p;
		for(String file : changedfiles)
		{
			String targetFile;
			if((targetFile = renameList.get(file)) == null)
				targetFile = file;
			log.info("Encoding " +file);
			log.info("Target file : "+targetFile);
			pb = new ProcessBuilder(ccTrack,repo+"/"+file,totalCommits+"",repo+"/"+targetFile);
			p = pb.start();
			InputStream stdout = p.getErrorStream();
	    	BufferedReader reader = new BufferedReader (new InputStreamReader(stdout));
	    	String line;
	    	while ((line = reader.readLine ()) != null) {
	    		log.info(line);
	    	}
	    	p.waitFor();
		}
		
	}

	private static ArrayList<String> getCommitFiles(String commitHash, boolean isRoot, String repo) throws IOException, InterruptedException {
		String[] cmdRoot = {"git","diff-tree","--no-commit-id", "--name-only", "-r", "--root", commitHash};
		String[] cmd = {"git","diff-tree","--no-commit-id", "--name-only", "-r", commitHash};
		ProcessBuilder pb1;
		ArrayList<String> files = new ArrayList<String>();
		if(isRoot)
			pb1 = new ProcessBuilder(cmdRoot);
		else
			pb1 = new ProcessBuilder(cmd);
		
    	pb1.directory(new File(repo));
    	pb1.redirectErrorStream(true);
    	Process p1 = pb1.start();
    	InputStream stdout = p1.getInputStream ();
    	BufferedReader reader = new BufferedReader (new InputStreamReader(stdout));
    	String line;
    	while ((line = reader.readLine ()) != null) {
    		files.add(line);
    	}
    	int exitCode1 = p1.waitFor();
		return files;
		
		
	}

	private static void checkout(String commitHash, File repoDir) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder("git","checkout", "-f", commitHash);
        pb.directory(repoDir);
        Process p = pb.start();
        int exitCode = p.waitFor();
        if(exitCode != 0)
        {
        	log.info("Error: Git Checkout failed");
        }
		
	}
}
