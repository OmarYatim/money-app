package com.moneyapp.backend.auth.repository;

import com.moneyapp.backend.auth.entity.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

  Optional<AppUser> findByEmail(String email);

  Optional<AppUser> findByPowensUserId(String powensUserId);

  @Query(
      """
      select distinct appUser
      from AppUser appUser
      where exists (
        select 1
        from UserConnection connection
        where connection.userId = appUser.id
          and connection.status = 'active'
      )
      """)
  List<AppUser> findUsersWithActiveConnections();
}
