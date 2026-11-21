import unittest

from recommendation_engine import RecommendationEngine


class RecommendationEngineTests(unittest.TestCase):
    def test_second_degree_returns_mutual_count_and_excludes_existing(self):
        engine = RecommendationEngine(
            [("a", "b"), ("b", "c"), ("a", "c"), ("b", "d")],
            profiles={"a": {}, "c": {"name": "C"}, "d": {"name": "D"}},
        )
        result = engine.get_recommendations("a")
        self.assertEqual([item["userId"] for item in result["items"]], ["d"])
        self.assertEqual(result["items"][0]["mutualConnections"], 1)
        self.assertEqual(result["items"][0]["degree"], 2)

    def test_third_degree_and_profile_reasons(self):
        engine = RecommendationEngine(
            [("a", "b"), ("b", "c"), ("c", "d")],
            profiles={"a": {"skills": ["Java"], "interests": ["Backend"]},
                      "d": {"skills": ["java"], "interests": ["backend"]}},
        )
        results = engine.get_recommendations("a")["items"]
        result = next(item for item in results if item["userId"] == "d")
        self.assertEqual(result["userId"], "d")
        self.assertEqual(result["degree"], 3)
        self.assertEqual(result["sharedSkills"], ["Java"])
        self.assertGreaterEqual(len(result["reasons"]), 3)

    def test_pending_requests_are_excluded_and_pagination_is_stable(self):
        engine = RecommendationEngine(
            [(1, 2), (2, 3), (2, 4)], pending_outgoing=[(1, 3)]
        )
        self.assertEqual(engine.get_recommendations(1)["total"], 1)
        self.assertEqual(engine.get_recommendations(1, page=1, size=1)["items"], [])

    def test_goal_and_semantic_skill_fit_improve_ranking(self):
        engine = RecommendationEngine(
            [("a", "b"), ("b", "c"), ("b", "d")],
            profiles={
                "a": {"skills": "Machine learning, Java", "goals": "Mentorship, AI collaboration"},
                "c": {"skills": "ML, Java", "goals": "Mentoring, collaborating on AI"},
                "d": {"skills": "Accounting", "goals": "Changing careers into finance"},
            },
        )
        result = engine.get_recommendations("a")["items"]
        self.assertEqual(result[0]["userId"], "c")
        self.assertGreater(result[0]["goalAlignment"], result[1]["goalAlignment"])
        self.assertGreater(result[0]["skillSimilarity"], result[1]["skillSimilarity"])
        self.assertTrue(any("goals align" in reason for reason in result[0]["reasons"]))

    def test_empty_graph_and_bad_pagination(self):
        engine = RecommendationEngine()
        self.assertEqual(engine.get_recommendations("missing")["items"], [])
        with self.assertRaises(ValueError):
            engine.get_recommendations("missing", page=-1)


if __name__ == "__main__":
    unittest.main()
