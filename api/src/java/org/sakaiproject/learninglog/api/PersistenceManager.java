package org.sakaiproject.learninglog.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.learninglog.api.sql.SQLGenerator;

import org.sakaiproject.authz.api.Member;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;

import lombok.Setter;

public class PersistenceManager {

	private final Logger logger = Logger.getLogger(PersistenceManager.class);

	private SQLGenerator sqlGenerator;

    @Setter
	private SakaiProxy sakaiProxy;

	public void init() {
		
		sqlGenerator = new SQLGenerator();

		if (sakaiProxy.isAutoDDL()) {
			if (!setupTables()) {
				logger.error("Failed to setup the tables");
			}
		}
	}

	public boolean setupTables() {

		Connection connection = null;
		Statement statement = null;

		try {
			connection = sakaiProxy.borrowConnection();
			boolean oldAutoCommitFlag = connection.getAutoCommit();
			connection.setAutoCommit(false);

			try {
			    statement = connection.createStatement();
				
				for (String sql : sqlGenerator.getCreateTablesStatements()) {
					statement.executeUpdate(sql);
				}

				connection.commit();

				return true;
			} catch (Exception e) {
				logger.error("Caught exception whilst setting up tables. Message: " + e.getMessage() + ". Rolling back ...");
				connection.rollback();
			} finally {
				connection.setAutoCommit(oldAutoCommitFlag);
			}
		} catch (Exception e) {
			logger.error("Caught exception whilst setting up tables. Message: " + e.getMessage());
		} finally {
			if (statement != null) {
				try {
					statement.close();
				} catch (Exception e) {}
			}

			sakaiProxy.returnConnection(connection);
		}

		return false;
	}

    public boolean returnFirstUseInSiteThenSet(String siteId) {

		Connection conn = null;
		PreparedStatement st = null;
		PreparedStatement st2 = null;

		try {
			conn = sakaiProxy.borrowConnection();
			logger.debug("isAutoCommit: " + conn.getAutoCommit());
			st = sqlGenerator.getSelectUsedInSiteStatement(siteId, conn);
            ResultSet rs = st.executeQuery();
            boolean isFirst = !rs.next();
            rs.close();

            if (isFirst) {
                st2 = sqlGenerator.getInsertUsedInSiteStatement(siteId, conn);
                st2.execute();
                conn.commit();
            }

            return isFirst;
        } catch (Exception e) {
            logger.error("Failed to query first use table. Returning false ...", e);
            return false;
        } finally {
			if (st != null) {
				try {
					st.close();
				} catch (Exception e) {}
			}
			if (st2 != null) {
				try {
					st2.close();
				} catch (Exception e) {}
			}
			sakaiProxy.returnConnection(conn);
        }
    }

	public boolean existPost(String postId) throws Exception {

		Connection connection = null;
		Statement statement = null;

		try {
			connection = sakaiProxy.borrowConnection();
			statement = connection.createStatement();
			String sql = sqlGenerator.getSelectPost(postId);
			ResultSet rs = statement.executeQuery(sql);
			boolean exists = rs.next();
            rs.close();
			return exists;
		} finally {

			if (statement != null) {
				try {
					statement.close();
				} catch (Exception e) {}
			}

			sakaiProxy.returnConnection(connection);
		}
	}

	public List<Post> getAllPost(String placementId) throws Exception {
		return getAllPost(placementId, false);
	}

	public List<Post> getAllPost(String placementId, boolean populate) throws Exception {

		List<Post> result = new ArrayList<Post>();

		Connection connection = null;
		Statement statement = null;

		try {
			connection = sakaiProxy.borrowConnection();
			statement = connection.createStatement();
			ResultSet rs = statement.executeQuery(sqlGenerator.getSelectAllPost(placementId));
			result = transformResultSetInPostCollection(rs, connection);
            rs.close();
		} finally {

			if (statement != null) {
				try {
					statement.close();
				} catch (Exception e) {}
			}

			sakaiProxy.returnConnection(connection);
		}

		return result;
	}

	public Comment getComment(String commentId) throws Exception {

		Connection connection = null;
		Statement st = null;

		try {
			connection = sakaiProxy.borrowConnection();
			st = connection.createStatement();
			String sql = sqlGenerator.getSelectComment(commentId);
			ResultSet rs = st.executeQuery(sql);
            if(rs.next()) {
                Comment comment = getCommentFromResult(rs);
                rs.close();
                return comment;
            } else {
                rs.close();
                throw new IdUnusedException("There is no comment with the id '" + commentId + "'");
            }
		} finally {

			if (st != null) {
				try {
					st.close();
				} catch (Exception e) {}
			}

			sakaiProxy.returnConnection(connection);
		}
	}

    private Comment getCommentFromResult(ResultSet rs) throws SQLException {

        String commentId = rs.getString("COMMENT_ID");
        String postId = rs.getString("POST_ID");
        String siteId = rs.getString("SITE_ID");
        String commentCreatorId = rs.getString("CREATOR_ID");
        Date commentCreatedDate = rs.getTimestamp("CREATED_DATE");
        Date commentModifiedDate = rs.getTimestamp("MODIFIED_DATE");
        String commentContent = rs.getString("CONTENT");

        Comment comment = new Comment();
        comment.setId(commentId);
        comment.setPostId(postId);
        comment.setSiteId(siteId);
        comment.setCreatorId(commentCreatorId);
        comment.setVisibility(rs.getString("VISIBILITY"));
        if (!comment.isAutoSave()) {
            comment.setAutosavedVersion(getAutosavedComment(commentId));
        }
        comment.setCreatedDate(commentCreatedDate.getTime());
        comment.setContent(commentContent);
        comment.setModifiedDate(commentModifiedDate.getTime());
        comment.setCreatorDisplayName(sakaiProxy.getDisplayNameForTheUser(comment.getCreatorId()));

        return comment;
    }

	public boolean saveComment(Comment comment) {
		
		Connection connection = null;
		List<PreparedStatement> statements = null;

		try {

            comment.setCreatorDisplayName(sakaiProxy.getDisplayNameForTheUser(comment.getCreatorId()));

			connection = sakaiProxy.borrowConnection();
			boolean oldAutoCommit = connection.getAutoCommit();
			connection.setAutoCommit(false);

			try {
                if (comment.isAutoSave()) {
                    statements = sqlGenerator.getInsertStatementsForAutoSavedComment(comment, connection);
                } else {
                    statements = sqlGenerator.getInsertStatementsForComment(comment, connection);
                }

				for (PreparedStatement st : statements) {
					st.executeUpdate();
                }

				connection.commit();

				return true;
			} catch (Exception e) {
				logger.error("Caught exception whilst saving comment. Rolling back ...", e);
				connection.rollback();
			} finally {
				connection.setAutoCommit(oldAutoCommit);
			}
		} catch (Exception e) {
			logger.error("Caught exception whilst saving comment.", e);
		} finally {
			if (statements != null) {
				for (PreparedStatement st : statements) {
					try {
						st.close();
					} catch (Exception e) {}
				}
			}

			sakaiProxy.returnConnection(connection);
		}

		return false;
	}

	public boolean deleteComment(String commentId) {
		
		Connection connection = null;
		List<PreparedStatement> statements = null;

		try {

			connection = sakaiProxy.borrowConnection();
			boolean oldAutoCommit = connection.getAutoCommit();
			connection.setAutoCommit(false);

			try {

				statements = sqlGenerator.getDeleteStatementsForComment(commentId, connection);

				for (PreparedStatement st : statements) {
					st.executeUpdate();
                }

				connection.commit();

				return true;
			} catch (Exception e) {
				logger.error("Caught exception whilst deleting comment. Rolling back ...", e);
				connection.rollback();
			} finally {
				connection.setAutoCommit(oldAutoCommit);
			}
		} catch (Exception e) {
			logger.error("Caught exception whilst deleting comment.", e);
		} finally {
			if (statements != null) {
				for (PreparedStatement st : statements) {
					try {
						st.close();
					} catch (Exception e) {}
				}
			}

			sakaiProxy.returnConnection(connection);
		}

		return false;
	}

	public boolean savePost(Post post, boolean isPublishing) {

		Connection connection = null;
		List<PreparedStatement> statements = null;
		List<PreparedStatement> attachmentStatements = null;

		try {

			connection = sakaiProxy.borrowConnection();
			boolean oldAutoCommit = connection.getAutoCommit();
			connection.setAutoCommit(false);

			try {

				statements = sqlGenerator.getInsertStatementsForPost(post, connection);

				for (PreparedStatement st : statements) {
					st.executeUpdate();
				}
				
				attachmentStatements = sqlGenerator.getInsertStatementsForAttachments(post, connection);
				
				for (PreparedStatement st : attachmentStatements) {
                    st.executeUpdate();
				}

                sakaiProxy.addAttachments(post, isPublishing);

				connection.commit();

				return true;
			} catch (Exception e) {
				logger.error("Caught exception whilst saving post. Rolling back ...", e);
				connection.rollback();
			} finally {
				connection.setAutoCommit(oldAutoCommit);
			}
		} catch (Exception e) {
			logger.error("Caught exception whilst saving post.", e);
		} finally {
			if (statements != null) {
				for (PreparedStatement st : statements) {
					try {
						st.close();
					} catch (Exception e) {}
				}
			}

			if (attachmentStatements != null) {
				for (PreparedStatement st : attachmentStatements) {
					try {
						st.close();
					} catch (Exception e) {}
				}
			}

			sakaiProxy.returnConnection(connection);
		}

		return false;
	}

	public boolean deletePost(Post post) {

		Connection connection = null;
		List<PreparedStatement> statements = null;

		try {

			connection = sakaiProxy.borrowConnection();
			boolean oldAutoCommit = connection.getAutoCommit();
			connection.setAutoCommit(false);

			try {

				statements = sqlGenerator.getDeleteStatementsForPost(post, connection);

				for (PreparedStatement st : statements) {
					st.executeUpdate();
                }

                sakaiProxy.deleteAttachments(post);

				connection.commit();

				return true;
			} catch (Exception e) {
				logger.error("Caught exception whilst deleting post. Rolling back ...", e);
				connection.rollback();
			} finally {
				connection.setAutoCommit(oldAutoCommit);
			}
		} catch (Exception e) {
			logger.error("Caught exception whilst deleting post.", e);
		} finally {
			if (statements != null) {
				for (PreparedStatement st : statements) {
					try {
						st.close();
					} catch (Exception e) {}
				}
			}

			sakaiProxy.returnConnection(connection);
		}

		return false;
	}

	public boolean recyclePost(Post post) {

		Connection connection = null;
		List<PreparedStatement> statements = null;

		try {
			connection = sakaiProxy.borrowConnection();
			boolean oldAutoCommit = connection.getAutoCommit();
			connection.setAutoCommit(false);

			try {
				statements = sqlGenerator.getRecycleStatementsForPost(post, connection);

				for (PreparedStatement st : statements) {
					st.executeUpdate();
                }

				connection.commit();
				return true;
			} catch (Exception e) {
				logger.error("Caught exception whilst recycling post. Rolling back ...", e);
				connection.rollback();
			} finally {
				connection.setAutoCommit(oldAutoCommit);
			}
		} catch (Exception e) {
			logger.error("Caught exception whilst recycling post.", e);
			return false;
		} finally {
			if (statements != null) {
				for (PreparedStatement st : statements) {
					try {
						st.close();
					} catch (Exception e) {}
				}
			}

			sakaiProxy.returnConnection(connection);
		}

		return false;
	}

	public boolean restorePost(String postId) {

		Connection connection = null;
		List<PreparedStatement> statements = null;

		try {
			Post post = getPost(postId);
			connection = sakaiProxy.borrowConnection();
			boolean oldAutoCommit = connection.getAutoCommit();
			connection.setAutoCommit(false);

			try {
				statements = sqlGenerator.getRestoreStatementsForPost(post, connection);

				for (PreparedStatement st : statements) {
					st.executeUpdate();
                }

				connection.commit();

				return true;
			} catch (Exception e) {
				logger.error("Caught exception whilst recycling post. Rolling back ...", e);
				connection.rollback();
			} finally {
				connection.setAutoCommit(oldAutoCommit);
			}
		} catch (Exception e) {
			logger.error("Caught exception whilst recycling post.", e);
			return false;
		} finally {
			if (statements != null) {
				for (PreparedStatement st : statements) {
					try {
						st.close();
					} catch (Exception e) {}
				}
			}

			sakaiProxy.returnConnection(connection);
		}

		return false;
	}

	public List<Post> getPosts(QueryBean query) throws Exception {
		
		List<Post> posts = new ArrayList<Post>();

		Connection connection = null;
		Statement st = null;

		try {
			connection = sakaiProxy.borrowConnection();
			st = connection.createStatement();
			List<String> sqlStatements = sqlGenerator.getSelectStatementsForQuery(query);
			for (String sql : sqlStatements) {
				ResultSet rs = st.executeQuery(sql);
				posts.addAll(transformResultSetInPostCollection(rs, connection));
                rs.close();
			}
		} finally {

			if (st != null) {
				try {
					st.close();
				} catch (Exception e) {}
			}

			sakaiProxy.returnConnection(connection);
		}

		return posts;
	}

	public Post getPost(String postId) throws Exception {

		Connection connection = null;
		Statement st = null;
		try {
			connection = sakaiProxy.borrowConnection();
			st = connection.createStatement();
			String sql = sqlGenerator.getSelectPost(postId);
			ResultSet rs = st.executeQuery(sql);
			List<Post> posts = transformResultSetInPostCollection(rs, connection);
			rs.close();

			if (posts.size() == 0) {
				throw new Exception("getPost: Unable to find post with id:" + postId);
            } else if (posts.size() > 1) {
				throw new Exception("getPost: there is more than one post with id:" + postId);
            }

			return posts.get(0);
		} finally {

			if (st != null) {
				try {
					st.close();
				} catch (Exception e) {}
			}

			sakaiProxy.returnConnection(connection);
		}
	}

	private List<Post> transformResultSetInPostCollection(ResultSet rs, Connection connection) throws Exception {
		
		List<Post> result = new ArrayList<Post>();

		if (rs == null)
			return result;

		Statement commentST = null;
		Statement unsavedCommentST = null;

		try {
            commentST = connection.createStatement();
            unsavedCommentST = connection.createStatement();
            while (rs.next()) {
                Post post = new Post();
                String postId = rs.getString("POST_ID");
                post.setId(postId);
                String siteId = rs.getString("SITE_ID");
                post.setSiteId(siteId);
                String title = rs.getString("TITLE"); post.setTitle(title);

				String content = rs.getString("CONTENT");
				post.setContent(content);

				Date postCreatedDate = rs.getTimestamp("CREATED_DATE");
				post.setCreatedDate(postCreatedDate.getTime());

				Date postModifiedDate = rs.getTimestamp("MODIFIED_DATE");
				post.setModifiedDate(postModifiedDate.getTime());

				String postCreatorId = rs.getString("CREATOR_ID");
				post.setCreatorId(postCreatorId);

				String visibility = rs.getString("VISIBILITY");
				post.setVisibility(visibility);

                String toolId = sakaiProxy.getLearningLogToolId(siteId);
                post.setUrl(sakaiProxy.getServerUrl() + "/portal/directtool/" + toolId + "?state=post&postId=" + postId);

				String sql = sqlGenerator.getSelectComments(postId);
				ResultSet commentRS = commentST.executeQuery(sql);
                List<Comment> comments = transformResultSetInCommentCollection(commentRS);
				commentRS.close();

                // This should pick up all the autosaved comments that have never
                // been explicitly saved as draft or published.
				sql = sqlGenerator.getSelectUnsavedAutosavedComments(postId);
				ResultSet unsavedCommentRS = unsavedCommentST.executeQuery(sql);
                comments.addAll(transformResultSetInCommentCollection(unsavedCommentRS));
				unsavedCommentRS.close();

                post.setComments(comments);

				post.setCreatorDisplayName(sakaiProxy.getDisplayNameForTheUser(post.getCreatorId()));

				sql = sqlGenerator.getMessageAttachmentsSelectStatement(post.getId());

				Statement st = null;

				List<Attachment> attachments = new ArrayList<Attachment>();

				try {
					st = connection.createStatement();
					ResultSet rs2 = st.executeQuery(sql);

					while (rs2.next()) {
						int id = rs2.getInt("ID");
						String name = rs2.getString("NAME");

						Attachment attachment = new Attachment();
						attachment.id = id;
						attachment.name = name;

						sakaiProxy.getAttachment(post, attachment);

						attachments.add(attachment);
					}

                    rs2.close();
				} finally {

					if (st != null) {
						try {
							st.close();
						} catch (Exception e) {}
					}
				}

				post.setAttachments(attachments);

				result.add(post);
			}
		} finally {

			if (commentST != null) {
				try {
					commentST.close();
				} catch (Exception e) {}
			}

			if (unsavedCommentST != null) {
				try {
					unsavedCommentST.close();
				} catch (Exception e) {}
			}
		}

		return result;
	}

	private List<Comment> transformResultSetInCommentCollection(ResultSet rs) throws Exception {
		
		List<Comment> result = new ArrayList<Comment>();

		if (rs == null) {
			return result;
        }

        while (rs.next()) {
            result.add(getCommentFromResult(rs));
        }

        return result;
    }

	public boolean postExists(String postId) throws Exception {
		
		Connection connection = null;
		Statement st = null;

		try {
			connection = sakaiProxy.borrowConnection();
			st = connection.createStatement();
			String sql = sqlGenerator.getSelectPost(postId);
			ResultSet rs = st.executeQuery(sql);
			boolean exists = rs.next();
            rs.close();
			return exists;
		} finally {

			if (st != null) {
				try {
					st.close();
				} catch (Exception e) {}
			}

			sakaiProxy.returnConnection(connection);
		}
	}

	public boolean populateAuthorData(BlogMember author, String siteId) {
		
		Connection connection = null;
		Statement st = null;

		try {
			connection = sakaiProxy.borrowConnection();
			st = connection.createStatement();
			String sql = sqlGenerator.getSelectAuthorStatement(author.getUserId(), siteId);
			ResultSet rs = st.executeQuery(sql);
			if (rs.next()) {
				int totalPosts = rs.getInt("TOTAL_POSTS");
				int totalComments = rs.getInt("TOTAL_COMMENTS");

				Timestamp lastPostTimestamp = rs.getTimestamp("LAST_POST_DATE");
				if (null != lastPostTimestamp) {

					long lastPostDate = lastPostTimestamp.getTime();

					Timestamp lastCommentTimestamp = rs.getTimestamp("LAST_COMMENT_DATE");
					if (lastCommentTimestamp != null)
						author.setDateOfLastComment(lastCommentTimestamp.getTime());

					String lastCommentAuthor = rs.getString("LAST_COMMENT_AUTHOR");
					author.setNumberOfPosts(totalPosts);
					author.setNumberOfComments(totalComments);
					author.setDateOfLastPost(lastPostDate);
					author.setLastCommentCreator(sakaiProxy.getDisplayNameForTheUser(lastCommentAuthor));
				}
			}

			rs.close();

			return true;
		} catch (Exception e) {
			logger.error("Caught exception whilst populating author data. Returning false ...", e);
			return false;
		} finally {

			if (st != null) {
				try {
					st.close();
				} catch (Exception e) {}
			}

			sakaiProxy.returnConnection(connection);
		}
	}

	public Map<String, String> getRoles(String siteId) {

		Map<String, String> tutorRoles = new HashMap<String, String>();

		Connection connection = null;
		PreparedStatement st = null;
		try {
			connection = sakaiProxy.borrowConnection();
			st = sqlGenerator.getSelectRolesStatement(siteId, connection);
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				String sakaiRole = rs.getString("SAKAI_ROLE");
				String llRole = rs.getString("LL_ROLE");
				tutorRoles.put(sakaiRole, llRole);
			}

            rs.close();

			return tutorRoles;
		} catch (Exception e) {
			logger.error("Caught exception whilst getting roles. Returning null ...", e);
			return null;
		} finally {

            if (st != null) {
			    try {
					st.close();
			    } catch (Exception e) {}
            }

			sakaiProxy.returnConnection(connection);
		}
	}

	public String getLLRole(String siteId, String sakaiRole) {
		
		Connection connection = null;
		PreparedStatement st = null;

		try {
			connection = sakaiProxy.borrowConnection();
			st = sqlGenerator.getSelectLLRoleStatement(siteId, sakaiRole, connection);
			ResultSet rs = st.executeQuery();
			if (rs.next()) {
				String llRole = rs.getString("LL_ROLE");
                rs.close();
				return llRole;
            } else {
				logger.error("No LL role for site '" + siteId + "' and sakai Role '" + sakaiRole + "'. Returning null ...");
                rs.close();
				return null;
			}
		} catch (Exception e) {
			logger.error("Caught exception whilst getting LL role. Returning null ...", e);
			return null;
		} finally {

            if (st != null) {
			    try {
					st.close();
			    } catch (Exception e) {}
            }

			sakaiProxy.returnConnection(connection);
		}
	}

	public boolean saveRoles(String siteId, Map<String, String> map) {
		
		Connection connection = null;

		List<PreparedStatement> sqls = null;

		try {
			connection = sakaiProxy.borrowConnection();

			boolean oldAutoCommit = connection.getAutoCommit();

			try {
				connection = sakaiProxy.borrowConnection();

				connection.setAutoCommit(false);

				sqls = sqlGenerator.getSaveRolesStatements(siteId, map, connection);

				for (PreparedStatement st : sqls) {
					st.executeUpdate();
                }

				connection.commit();
			} catch (Exception e) {
				logger.error("Caught exception whilst deleting post. Rolling back ...", e);
				connection.rollback();
			} finally {
				connection.setAutoCommit(oldAutoCommit);
			}
		} catch (Exception e) {
			logger.error("Caught exception whilst marking tutor roles", e);
		} finally {
			if (sqls != null) {
				for (PreparedStatement st : sqls) {
					try {
						st.close();
					} catch (Exception e) {}
				}
			}

			sakaiProxy.returnConnection(connection);
		}

		return false;
	}

	public boolean deleteAttachment(String siteId, String name, String postId) {

		Connection connection = null;
		PreparedStatement statement = null;

		try {
			connection = sakaiProxy.borrowConnection();
			boolean oldAutoCommit = connection.getAutoCommit();
			connection.setAutoCommit(false);

			try {
				statement = sqlGenerator.getDeleteStatementForAttachment(name, postId, connection);
				statement.executeUpdate();

				sakaiProxy.deleteAttachment(name);

				connection.commit();

				return true;
			} catch (Exception e) {
				logger.error("Caught exception whilst deleting attachment. Rolling back ...", e);
				connection.rollback();
			} finally {
				connection.setAutoCommit(oldAutoCommit);
			}
		} catch (Exception e) {
			logger.error("Caught exception whilst deleting attachment.", e);
		} finally {
			if (statement != null) {
				try {
					statement.close();
				} catch (Exception e) {
				}
			}

			sakaiProxy.returnConnection(connection);
		}

		return false;
	}

	public Comment getAutosavedComment(String commentId) {

		if (logger.isDebugEnabled()) {
			logger.debug("getAutosavedComment(" + commentId + ")");
        }

		Connection connection = null;
		PreparedStatement st = null;
		try {
			connection = sakaiProxy.borrowConnection();
			st = sqlGenerator.getSelectAutosavedComment(commentId, connection);
			ResultSet rs = st.executeQuery();
			List<Comment> comments = transformResultSetInCommentCollection(rs);

			if (comments.size() == 0) {
				return null;
			}
			if (comments.size() > 1) {
				logger.error("getAutosavedComment: there is more than one comment with id:" + commentId);
				return null;
			}

			return comments.get(0);
		} catch (Exception e) {
			logger.error("Caught exception whilst getting autosaved comment", e);
			return null;
		} finally {

			if (st != null) {
				try {
					st.close();
				} catch (Exception e) {
				}
			}

			sakaiProxy.returnConnection(connection);
		}
	}

    public boolean setGroupMode(String siteId, String groupMode) {

        if (logger.isDebugEnabled()) {
			logger.debug("setGroupMode(" + siteId + "," + groupMode + ")");
        }

		Connection connection = null;
		PreparedStatement st = null;
		try {
			connection = sakaiProxy.borrowConnection();
			st = sqlGenerator.getInsertOrUpdateGroupMode(siteId, groupMode, connection);
			int result = st.executeUpdate();
			connection.commit();
			return true;
		} catch (Exception e) {
			logger.error("Caught exception whilst setting group mode", e);
			return false;
		} finally {

			if (st != null) {
				try {
					st.close();
				} catch (Exception e) { }
			}

			sakaiProxy.returnConnection(connection);
		}
    }

    public boolean isGroupMode(String siteId) {

        if (logger.isDebugEnabled()) {
			logger.debug("isGroupMode(" + siteId + ")");
        }

		Connection connection = null;
		PreparedStatement st = null;
		try {
			connection = sakaiProxy.borrowConnection();
			st = sqlGenerator.getSelectGroupMode(siteId, connection);
			ResultSet rs = st.executeQuery();

            if (rs.next() && rs.getString("GROUP_MODE").equals("Y")) {
                return true;
            } else {
                return false;
            }
		} catch (Exception e) {
			logger.error("Caught exception whilst getting group mode", e);

            // This is the more secure response
			return true;
		} finally {

			if (st != null) {
				try {
					st.close();
				} catch (Exception e) { }
			}

			sakaiProxy.returnConnection(connection);
        }
    }

    public boolean setEmailsMode(String siteId, String emailsMode) {

        if (logger.isDebugEnabled()) {
			logger.debug("setEmailsMode(" + siteId + "," + emailsMode + ")");
        }

		Connection connection = null;
		PreparedStatement st = null;
		try {
			connection = sakaiProxy.borrowConnection();
			st = sqlGenerator.getInsertOrUpdateEmailsMode(siteId, emailsMode, connection);
			int result = st.executeUpdate();
            connection.commit();
			return true;
		} catch (Exception e) {
			logger.error("Caught exception whilst setting emails mode", e);
			return false;
		} finally {
			if (st != null) {
				try {
					st.close();
				} catch (Exception e) { }
			}

			sakaiProxy.returnConnection(connection);
		}
    }

    public boolean isEmailsMode(String siteId) {

        if (logger.isDebugEnabled()) {
			logger.debug("isEmailsMode(" + siteId + ")");
        }

		Connection connection = null;
		PreparedStatement st = null;
		try {
			connection = sakaiProxy.borrowConnection();
			st = sqlGenerator.getSelectEmailsMode(siteId, connection);
			ResultSet rs = st.executeQuery();

            if (rs.next() && rs.getString("EMAILS_MODE").equals("Y")) {
                return true;
            } else {
                return false;
            }
		} catch (Exception e) {
			logger.error("Caught exception whilst getting emails mode", e);

            // This is the more secure response
			return true;
		} finally {

			if (st != null) {
				try {
					st.close();
				} catch (Exception e) { }
			}

			sakaiProxy.returnConnection(connection);
        }
    }

    public Set<String> getTutorsForStudent(String studentId, String siteId) {

        Set<String> tutors = new HashSet<String>();

        if (isGroupMode(siteId)) {
            Site site = sakaiProxy.getSite(siteId);
            for (Group group : site.getGroupsWithMember(studentId)) {
                for (Member member : group.getMembers()) {
                    if (getLLRole(siteId, member.getRole().getId()).equals(Roles.TUTOR)) {
                        tutors.add(member.getUserId());
                    }
                }
            }
        }

        return tutors;
    }
}
