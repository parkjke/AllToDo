import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import './Auth.css';

export default function Register() {
    const navigate = useNavigate();
    const [formData, setFormData] = useState({
        name: '',
        phone: '',
        address: '',
        id: '',
        password: '',
        passwordConfirm: ''
    });
    const [error, setError] = useState('');

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        if (!formData.name || !formData.id || !formData.password) {
            setError('필수 정보를 모두 입력해주세요.');
            return;
        }
        if (formData.password !== formData.passwordConfirm) {
            setError('비밀번호가 일치하지 않습니다.');
            return;
        }

        // Mock API Call
        alert('직원 등록 요청이 전송되었습니다.\n마스터 승인 후 로그인 가능합니다.');
        navigate('/login');
    };

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    return (
        <div className="auth-container">
            <div className="auth-card card" style={{ maxWidth: '450px' }}>
                <h1 className="auth-title">AllToDo Mng</h1>
                <p className="auth-subtitle">신규 직원 등록 신청</p>

                <form onSubmit={handleSubmit} className="auth-form">
                    <div className="form-group">
                        <label>이름</label>
                        <input name="name" type="text" className="input-field" placeholder="실명 입력" onChange={handleChange} />
                    </div>

                    <div className="form-group">
                        <label>전화번호</label>
                        <input name="phone" type="text" className="input-field" placeholder="010-0000-0000" onChange={handleChange} />
                    </div>

                    <div className="form-group">
                        <label>주소</label>
                        <input name="address" type="text" className="input-field" placeholder="주소 입력" onChange={handleChange} />
                    </div>

                    <div style={{ margin: '10px 0', borderTop: '1px solid #eee' }}></div>

                    <div className="form-group">
                        <label>아이디</label>
                        <input name="id" type="text" className="input-field" placeholder="사용할 ID" onChange={handleChange} />
                    </div>

                    <div className="form-group">
                        <label>비밀번호</label>
                        <input name="password" type="password" className="input-field" placeholder="비밀번호 (6자리 이상)" onChange={handleChange} />
                    </div>

                    <div className="form-group">
                        <label>비밀번호 확인</label>
                        <input name="passwordConfirm" type="password" className="input-field" placeholder="비밀번호 재입력" onChange={handleChange} />
                    </div>

                    {error && <div className="error-msg">{error}</div>}

                    <button type="submit" className="btn btn-primary btn-block">등록 요청</button>
                </form>

                <div className="auth-footer">
                    <Link to="/login">로그인 화면으로 돌아가기</Link>
                </div>
            </div>
        </div>
    );
}
