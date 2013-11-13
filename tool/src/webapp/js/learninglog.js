var blogCurrentUserPermissions = null;
var blogCurrentUserRole = null;
var blogCurrentPost = null;
var blogCurrentPosts = null;
var blogCurrentUser = null;
var blogHomeState = null;

var llAttachmentsDirty = false;

var autosave_id = null;

(function() {

    var llIsTutor = startupArgs.isTutor;

	// We need the toolbar in a template so we can swap in the translations
	SakaiUtils.renderTrimpathTemplate('blog_toolbar_template',{},'blog_toolbar');

	$('#blog_home_link > span > a').click(function(e) {
		return switchState('home');
	});

	$('#blog_create_post_link > span > a').click(function(e) {
		return switchState('createPost');
	});

	$('#blog_permissions_link > span > a').click(function(e) {
		return switchState('permissions');
	});

	$('#blog_recycle_bin_link > span > a').click(function(e) {
		return switchState('viewRecycled');
	});

	if(!llIsTutor) {
		$("#blog_create_post_link").show();
    } else {
		$("#blog_create_post_link").hide();
    }

	blogCurrentUser = SakaiUtils.getCurrentUser();
	
	if(!blogCurrentUser) {
		alert("No current user. Have you logged in?");
		return;
	}
	
    $.localise('learninglog-translations',{language:startupArgs.language,loadBase: true});
	
	blogCurrentUserPermissions = new LearningLogPermissions(LearningLogUtils.getCurrentUserPermissions());

	if(llIsTutor) {
		blogHomeState = 'viewMembers';
	} else {
		blogHomeState = 'userPosts';
	}

    var initialState = blogHomeState;

    if(startupArgs.postId != 'none') {
        initialState = 'post';
    }
	
	if(blogCurrentUserPermissions.modifyPermissions) {
		$("#blog_permissions_link").show();
		$("#blog_recycle_bin_link").show();
	}
	else {
		$("#blog_permissions_link").hide();
		$("#blog_recycle_bin_link").hide();
	}

	try {
		if(window.frameElement) {
			window.frameElement.style.minHeight = '600px';
		}
	} catch(err) {
		// This is likely under an LTI provision scenario
	}

	// Clear the autosave interval
	if(autosave_id) {
		clearInterval(autosave_id);
	}
	
	// Now switch into the requested state
	switchState(initialState,startupArgs);
})();

function switchState(state,args) {

	$('#cluetip').hide();

	$('#blog_toolbar > li > span').removeClass('current');
	
	if('home' === state) {

	    $('#blog_home_link > span').addClass('current');
		switchState(blogHomeState,args);
	} else if('viewMembers' === state) {

	    $('#blog_home_link > span').addClass('current');

		jQuery.ajax({
	    	url : "/direct/learninglog-author.json?siteId=" + startupArgs.blogSiteId,
	      	dataType : "json",
			cache: false,
		   	success : function(data) {
				var authors = data['learninglog-author_collection'];
				for(var i=0,j=authors.length;i<j;i++) {
					if(authors[i].dateOfLastPost > 0) {
						authors[i].formattedDateOfLastPost = LearningLogUtils.formatDate(authors[i].dateOfLastPost);
					} else {
						authors[i].formattedDateOfLastPost = "n/a";
                    }
					if(authors[i].dateOfLastComment > 0) {
						authors[i].formattedDateOfLastComment = LearningLogUtils.formatDate(authors[i].dateOfLastComment);
					} else {
						authors[i].formattedDateOfLastComment = "n/a";
                    }
				}
				SakaiUtils.renderTrimpathTemplate('blog_authors_content_template',{'authors':authors},'blog_content');

 				$(document).ready(function() {
 					LearningLogUtils.attachProfilePopup();
  									
  					$("#blog_author_table").tablesorter({
	 						cssHeader:'blogSortableTableHeader',
	 						cssAsc:'blogSortableTableHeaderSortUp',
	 						cssDesc:'blogSortableTableHeaderSortDown',
							textExtraction: 'complex',	
							sortList: [[0,0]],
	 						widgets: ['zebra'],
	 						headers:
	 						{
	 							2: {sorter: "isoDate"},
	 							4: {sorter: "isoDate"}
	 						}
                        }).tablesorterPager({container: $("#blogBloggerPager"),positionFixed: false});
	 						
                    resizeMainFrame();
	   			});
			},
			error : function(xmlHttpRequest,status,errorThrown) {
				alert("Failed to get authors. Reason: " + errorThrown);
			}
	   	});
	} else if('userPosts' === state) {
	    $('#blog_home_link > span').addClass('current');

		// Default to using the current session user id ...
		var userId = blogCurrentUser.id;
		
		// ... but override it with any supplied one
		if(args && args.userId)
			userId = args.userId;

		var url = "/direct/learninglog-post.json?siteId=" + startupArgs.blogSiteId + "&creatorId=" + userId;

		jQuery.ajax( {
	       	'url' : url,
	       	dataType : "text",
			cache: false,
		   	success : function(text) {
		   	
		   		var data = JSON.parse(text);

				var profileMarkup = SakaiUtils.getProfileMarkup(userId);

				blogCurrentPosts = data['learninglog-post_collection'];
                LearningLogUtils.addFormattedDatesToCurrentPosts();
	 			
				SakaiUtils.renderTrimpathTemplate('blog_user_posts_template',{'creatorId':userId,'posts':blogCurrentPosts},'blog_content');
				$('#blog_author_profile').html(profileMarkup);
	 			for(var i=0,j=blogCurrentPosts.length;i<j;i++) {
                    LearningLogUtils.addEscapedCreatorIdsToComments(blogCurrentPosts[i]);
					SakaiUtils.renderTrimpathTemplate('blog_post_template',blogCurrentPosts[i],'post_' + blogCurrentPosts[i].id);
                }

                $(document).ready(function() {

                    $('.ll_toggle_comments_link').click(function (e) {
                        var postId = this.id.substring(0,this.id.indexOf('_comments_toggle'));
                        $('#' + postId + '_comments').toggle();
                        resizeMainFrame();
                    });

                    resizeMainFrame();
                });
            },
			error : function(xmlHttpRequest,status,errorThrown) {
				alert("Failed to get posts. Reason: " + errorThrown);
			}
	   	});
	} else if('post' === state) {

		if(args && args.postId) {
			blogCurrentPost = LearningLogUtils.findPost(args.postId);
        }

		if(!blogCurrentPost) {
			return false;
        }
			
		LearningLogUtils.addFormattedDatesToCurrentPost();
        LearningLogUtils.addEscapedCreatorIdsToComments(blogCurrentPost);

		SakaiUtils.renderTrimpathTemplate('blog_post_page_content_template',blogCurrentPost,'blog_content');
		SakaiUtils.renderTrimpathTemplate('blog_post_template',blogCurrentPost,'post_' + blogCurrentPost.id);

	 	$(document).ready(function() {
			$('#blog_user_posts_link').click(function(e) {
				switchState('userPosts',{'userId' : blogCurrentPost.creatorId});
			});

			$('.content').show();

			if(blogCurrentPost.comments.length > 0) $('.comments').show();

            resizeMainFrame();
	 	});
	} else if('createPost' === state) {

	    $('#blog_create_post_link > span').addClass('current');

		var post = {id:'',title:'',content:'',commentable:true,attachments:[]};

		if(args && args.postId) {
			post = LearningLogUtils.findPost(args.postId);
        }

		SakaiUtils.renderTrimpathTemplate('blog_create_post_template',post,'blog_content');

	 	$(document).ready(function() {

            if(post.attachments) {
                $('#attachments-area').show();
            }
	 	
			$('#blog_save_post_button').click(function (e) { LearningLogUtils.savePostAsDraft(false); });

			$('#blog_publish_post_button').click(LearningLogUtils.publishPost);

			$('#blog_cancel_button').click(function(e) {
				switchState('home');
			});
			
 			SakaiUtils.setupWysiwygEditor('blog_content_editor',600,400,'Default',startupArgs.blogSiteId);
 			
	    	$('#ll_attachment').MultiFile( {
		       		max: 5,
					namePattern: '$name_$i',
                    afterFileAppend: function(element, value, master_element) {
                        llAttachmentsDirty = true;
                    }
				} );
				
			var savePostOptions = { 
				dataType: 'text',
				timeout: 30000,
				iframe: true,
   				success: function(text,statusText,xhr) {
   				
   					var post = JSON.parse(unescape(text));
   					
   					if(!post.isAutosave) {
						switchState(blogHomeState);
                    } else {
   						// Get rid of the populated upload fields or they may result
   						// in multiple copies of the same attachment, especially in
   						// the autosave scenario
                    	var mfs = $('.MultiFile-applied');
                    	for(var i=0,j=mfs.length - 1;i<j;i++) {
                        	$(mfs[i]).remove();
                    	}
                        $('.MultiFile-label').remove();
						$('#blog_post_id_field').val(post.id);
                        if(post.attachments.length > 0) {
                            // Now add the current attachments
                            var attachments = $('#attachments-area').show();
                            for(var i=0,j=post.attachments.length;i<j;i++) {
                                var attachment = post.attachments[i];
                                attachments.append("<div id=\"file_" + i +"\"><span>" + attachment.name + "</span><a href=\"#\" onclick=\"LearningLogUtils.removeAttachment('" + attachment.id + "','" + post.id + "','file_" + i +"');\" title=\"Delete attachment\"><img src=\"/library/image/silk/cross.png\" width=\"16\" height=\"16\"/></a><br /></div>");
                            }
                        }

						// Flash the autosaved message
						$('#learninglog_autosaved_message').show();
						setTimeout(function() {
								$('#learninglog_autosaved_message').fadeOut(200);
							},2000);

                        SakaiUtils.resetEditor('blog_content_editor');
                        llAttachmentsDirty = false;
					}
   				},
   				beforeSubmit: function(arr, $form, options) {
   					return true;
   				},
   				error : function(xmlHttpRequest,textStatus,errorThrown) {
   					alert('Error:' + errorThrown);
				}
   			};
				
   			$('#ll_post_form').ajaxForm(savePostOptions);

			// Start the auto saver
			autosave_id = setInterval(function() {
					if(!(SakaiUtils.isEditorDirty('blog_content_editor') || llAttachmentsDirty) || $('#blog_title_field').val().length < 4) {
						return;
					}
					
					LearningLogUtils.savePostAsDraft(true);
				},10000);
	 	});
	} else if('createComment' === state) {
	
		if(!args || !args.postId) {
			return;
        }

		blogCurrentPost = LearningLogUtils.findPost(args.postId);

		LearningLogUtils.addFormattedDatesToCurrentPost();

		var comment = {id: '',postId: args.postId,content: ''};

		if(args.commentId) {
			var comments = blogCurrentPost.comments;

			for(var i=0,j=comments.length;i<j;i++) {
				if(comments[i].id == args.commentId) {
					comment = comments[i];
					break;
				}
			}

			if(comment.autosavedVersion) {
				if(confirm('There is an autosaved version of this comment. Do you want to use that instead?')) {
					comment = comment.autosavedVersion;
				}
			}
		}

		SakaiUtils.renderTrimpathTemplate('blog_create_comment_template',comment,'blog_content');

		$(document).ready(function() {

			SakaiUtils.renderTrimpathTemplate('blog_post_template',blogCurrentPost,'blog_post_' + args.postId);

			$('#blog_save_comment_button').click(LearningLogUtils.saveCommentAsDraft);

			$('#blog_publish_comment_button').click(LearningLogUtils.publishComment);

			$('#blog_cancel_button').click(function (e) {
                switchState('userPosts',{'userId':blogCurrentPost.creatorId});
            });
			
 			SakaiUtils.setupWysiwygEditor('blog_content_editor',600,400);

			// Start the auto saver
			autosave_id = setInterval(function() {
					if(!SakaiUtils.isEditorDirty('blog_content_editor')) {
						return;
					}
					
					LearningLogUtils.autosaveComment();
				},10000);
		});
	} else if('permissions' === state) {

	    $('#blog_permissions_link > span').addClass('current');

		var roleMapList = LearningLogUtils.parsePermissions();

		SakaiUtils.renderTrimpathTemplate('blog_permissions_content_template',{'roleMapList':roleMapList},'blog_content');

	 	$(document).ready(function() {
			$('#blog_permissions_save_button').click(LearningLogUtils.savePermissions);
			$('#blog_permissions_cancel_button').click(function(e) {
				return switchState('home');
			});

            resizeMainFrame();
		});
	} else if('viewRecycled' === state) {

	    $('#blog_recycle_bin_link > span').addClass('current');

		jQuery.ajax( {
	       	url : "/direct/learninglog-post.json?siteId=" + startupArgs.blogSiteId + "&visibilities=RECYCLED",
	       	dataType : "json",
			cache: false,
		   	success : function(data) {

				var posts = data['learninglog-post_collection'];
	 			
				SakaiUtils.renderTrimpathTemplate('blog_recycled_posts_template',{'posts':posts},'blog_content');
	 			for(var i=0,j=posts.length;i<j;i++) {
                    LearningLogUtils.addFormattedDatesToPost(posts[i]);
                    LearningLogUtils.addEscapedCreatorIdsToComments(posts[i]);
					SakaiUtils.renderTrimpathTemplate('blog_post_template',posts[i],'post_' + posts[i].id);
                }

				$('#blog_really_delete_button').click(LearningLogUtils.deleteSelectedPosts);
				$('#blog_restore_button').click(LearningLogUtils.restoreSelectedPosts);

                $(document).ready(function() {
                    resizeMainFrame();
                });
			},
			error : function(xmlHttpRequest,status,errorThrown) {
				alert("Failed to get posts. Reason: " + errorThrown);
			}
	   	});
	}
}

function toggleFullContent(v) {
    $(document).ready(function() {
        resizeMainFrame();
    });
	
	if(v.checked) {

		$('.content').hide();
		$('.comments').hide();
		$('.comment_toggle').hide();
	} else {

		$('.content').show();
		$('.comments').show();
		$('.comment_toggle').show();
	}
}

function toggleComments(v) {
    $(document).ready(function() {
        resizeMainFrame();
    });
	
	if(v.checked) {
		$('.comments').show();
	} else {
		$('.comments').hide();
	}
}

function resizeMainFrame() {

    try {
        if(window.frameElement) {
            setMainFrameHeight(window.frameElement.id);
        }
    } catch(err) {
        // This is likely under an LTI provision scenario
    }
}
