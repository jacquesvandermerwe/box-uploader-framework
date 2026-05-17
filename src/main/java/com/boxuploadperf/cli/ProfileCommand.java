package com.boxuploadperf.cli;

import com.boxuploadperf.config.ProfileStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "profile", description = "Manage saved profiles",
        subcommands = {ProfileCommand.ListCmd.class, ProfileCommand.ShowCmd.class, ProfileCommand.DeleteCmd.class})
public class ProfileCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("Use: profile list | show <name> | delete <name>");
    }

    @Command(name = "list", description = "List saved profiles")
    static class ListCmd implements Runnable {
        @Override
        public void run() {
            try {
                ProfileStore store = new ProfileStore(RunCommand.defaultProfilesDir());
                for (String name : store.list()) {
                    System.out.println(name);
                }
            } catch (Exception e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }
        }
    }

    @Command(name = "show", description = "Show profile (secret redacted)")
    static class ShowCmd implements Runnable {
        @Parameters(index = "0", description = "Profile name")
        String name;

        @Override
        public void run() {
            try {
                ProfileStore store = new ProfileStore(RunCommand.defaultProfilesDir());
                System.out.println(store.redactedYaml(store.load(name)));
            } catch (Exception e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }
        }
    }

    @Command(name = "delete", description = "Delete a profile")
    static class DeleteCmd implements Runnable {
        @Parameters(index = "0", description = "Profile name")
        String name;

        @Override
        public void run() {
            try {
                ProfileStore store = new ProfileStore(RunCommand.defaultProfilesDir());
                store.delete(name);
                System.out.println("Deleted: " + name);
            } catch (Exception e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }
        }
    }
}
