import unittest
from unittest.mock import patch
import agent

class CloudAgentSafetyTests(unittest.TestCase):
    def test_only_allowlisted_actions_exist(self):
        self.assertEqual(set(agent.ACTIONS), {"disk_report", "memory_report", "failed_services", "recent_errors"})

    def test_unknown_action_is_rejected(self):
        with self.assertRaises(ValueError):
            agent.run_approved_action("arbitrary_shell")

    @patch("agent.subprocess.run")
    def test_fixed_command_is_used_without_shell(self, run):
        run.return_value.stdout = "Filesystem Size Used Avail Use% Mounted on\n"
        run.return_value.returncode = 0
        result = agent.run_approved_action("disk_report")
        args, kwargs = run.call_args
        self.assertEqual(args[0], ["df", "-hT", "--", "/"])
        self.assertFalse(kwargs.get("shell", False))
        self.assertEqual(result["exit_code"], 0)

if __name__ == "__main__":
    unittest.main()
