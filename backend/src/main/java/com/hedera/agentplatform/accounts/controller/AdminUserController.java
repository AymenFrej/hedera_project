package com.hedera.agentplatform.accounts.controller;

import com.hedera.agentplatform.accounts.dto.*;
import com.hedera.agentplatform.accounts.service.AdminUserService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AdminUserService service;
    public AdminUserController(AdminUserService service){this.service=service;}
    @GetMapping public List<ManagedUserResponse> list(@RequestHeader(value="Authorization",required=false) String auth){return service.list(auth);}
    @PostMapping public ManagedUserResponse create(@RequestHeader(value="Authorization",required=false) String auth,@RequestBody CreateManagedUserRequest request){return service.create(auth,request);}
    @PutMapping("/{id}/role") public ManagedUserResponse role(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id,@RequestBody RoleUpdateRequest request){return service.updateRole(auth,id,request);}
    @DeleteMapping("/{id}") public void delete(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id){service.delete(auth,id);}
    @PutMapping("/{id}/profile") public ManagedUserResponse profile(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id,@RequestBody ProfileUpdateRequest request){return service.updateProfile(auth,id,request);}
    @PostMapping("/{id}/restore") public ManagedUserResponse restore(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id,@RequestBody RoleUpdateRequest request){return service.restore(auth,id,request);}
    @PostMapping("/{id}/wallet") public ManagedUserResponse wallet(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id){return service.provisionWallet(auth,id);}
    @DeleteMapping("/{id}/mock") public void deleteMock(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id){service.deleteMock(auth,id);}
}
